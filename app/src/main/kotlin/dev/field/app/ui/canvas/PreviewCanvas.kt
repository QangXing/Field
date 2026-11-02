package dev.field.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.FieldDrag
import dev.field.app.ui.theme.FieldElectric
import dev.field.app.ui.theme.FieldForce
import dev.field.app.ui.theme.FieldGravity
import dev.field.app.ui.theme.FieldMagnetic
import dev.field.app.ui.theme.GridLine
import dev.field.app.ui.theme.GridLineMajor
import dev.field.app.ui.theme.SurfaceRaised
import dev.field.app.ui.theme.TextPrimary
import dev.field.core.fields.DragRegion
import dev.field.core.fields.FieldKind
import dev.field.core.fields.FieldModel
import dev.field.core.fields.FieldShape
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.scene.Scene
import dev.field.core.scene.toEngine
import dev.field.core.sim.Body
import dev.field.core.sim.BodyShape
import dev.field.core.sim.Probe
import dev.field.core.viz.ArrowSample
import dev.field.core.viz.FieldViz
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class PreviewArrow(val sample: ArrowSample, val color: Color)

private fun buildPreviewArrows(field: FieldModel, bounds: Aabb): List<PreviewArrow> {
    val kinds = mutableListOf<Pair<FieldKind, Color>>()
    if (field.sources.any { it is PointCharge }) kinds.add(FieldKind.ELECTRIC to FieldElectric)
    if (field.sources.any { it is MassSource || it is GravityZone }) kinds.add(FieldKind.GRAVITY to FieldGravity)
    if (field.sources.any { it is ForceZone }) kinds.add(FieldKind.UNIFORM_FORCE to FieldForce)
    if (field.sources.any { it is DragRegion }) kinds.add(FieldKind.DRAG to FieldDrag)

    val arrows = ArrayList<PreviewArrow>(kinds.size * 256)
    for ((kind, color) in kinds) {
        FieldViz.arrows(field, bounds, cols = 20, rows = 20, kind = kind).forEach {
            arrows.add(PreviewArrow(it, color))
        }
    }
    return arrows
}

/**
 * Read-only render of a saved scene, used for save previews. The camera is
 * supplied by the caller so the menu can frame a save without mutating it.
 */
@Composable
fun ScenePreview(
    scene: Scene,
    center: Vec2,
    zoom: Double,
    modifier: Modifier = Modifier,
    showArrows: Boolean = true,
) {
    val field = remember(scene.sources, scene.world.gravity, scene.rules.gravityEnabled) {
        FieldModel(scene.sources, scene.world.gravity, gravityEnabled = scene.rules.gravityEnabled)
    }
    val arrows = remember(field, scene.world.bounds, showArrows) {
        if (showArrows) buildPreviewArrows(field, scene.world.bounds) else emptyList()
    }
    val probes = remember(scene.probes) {
        scene.probes.map { Probe(it.id, it.position, it.velocity, it.mass, it.charge, it.radius) }
    }
    val bodies = remember(scene.bodies) {
        scene.bodies.map {
            Body(it.id, it.position, it.velocity, it.mass, it.charge, it.shape, it.size, it.angle)
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawPreviewScene(scene, arrows, probes, bodies, center, zoom, size)
    }
}

/**
 * Live preview used as the menu backdrop: the saved scene keeps simulating, so
 * bodies and probes move behind the dimmed menu.
 */
@Composable
fun AnimatedScenePreview(
    scene: Scene,
    center: Vec2,
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    val engine = remember(scene) {
        toEngine(
            scene.copy(
                probes = scene.probes.take(600),
                bodies = scene.bodies.take(80),
            ),
        )
    }
    val hasMotion = scene.probes.isNotEmpty() || scene.bodies.isNotEmpty()
    var frame by remember { mutableStateOf(0) }

    LaunchedEffect(engine, hasMotion) {
        if (!hasMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var accumulator = 0.0
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - last) / 1.0e9).coerceIn(0.0, 0.05)
            last = now
            accumulator += elapsed
            var steps = 0
            while (accumulator >= engine.settings.fixedDt && steps < 2) {
                runCatching { engine.step(engine.settings.fixedDt) }
                accumulator -= engine.settings.fixedDt
                steps++
            }
            if (accumulator > engine.settings.fixedDt) accumulator = 0.0
            frame++
        }
    }

    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frame
        drawPreviewScene(scene, emptyList(), engine.probes, engine.bodies, center, zoom, size)
    }
}

private fun DrawScope.drawPreviewScene(
    scene: Scene,
    arrows: List<PreviewArrow>,
    probes: List<Probe>,
    bodies: List<Body>,
    center: Vec2,
    zoom: Double,
    canvas: Size,
) {
    val width = canvas.width
    val height = canvas.height
    if (width <= 0f || height <= 0f) return
    val z = zoom.toFloat()
    fun sx(x: Double) = width / 2f + (x - center.x).toFloat() * z
    fun sy(y: Double) = height / 2f - (y - center.y).toFloat() * z
    fun screen(p: Vec2) = Offset(sx(p.x), sy(p.y))

    drawRect(Bg)
    drawPreviewGrid(scene.world.bounds, ::sx, ::sy, z, width, height)

    for (source in scene.sources) {
        when (source) {
            is GravityZone -> drawPreviewZone(source.min, source.max, source.shape, FieldGravity, ::sx, ::sy, z)
            is ForceZone -> drawPreviewZone(source.min, source.max, source.shape, FieldForce, ::sx, ::sy, z)
            is DragRegion -> drawPreviewZone(source.min, source.max, source.shape, FieldDrag, ::sx, ::sy, z)
            else -> Unit
        }
    }

    drawPreviewArrows(arrows, scene.world.bounds, ::sx, ::sy, z)

    for (source in scene.sources) {
        when (source) {
            is WallSegment -> drawLine(
                TextPrimary.copy(alpha = 0.75f),
                Offset(sx(source.start.x), sy(source.start.y)),
                Offset(sx(source.end.x), sy(source.end.y)),
                strokeWidth = max(2f, source.thickness.toFloat() * z),
                cap = androidx.compose.ui.graphics.StrokeCap.Square,
            )

            is PointCharge -> {
                val color = if (source.charge >= 0.0) FieldElectric else Danger
                drawPreviewUniformRegion(source.position, source.extent, source.shape, color, ::sx, ::sy, z)
                val sign = if (source.charge >= 0.0) 1.0 else -1.0
                val c = screen(source.position)
                val dx = (cos(source.angle) * sign).toFloat()
                val dy = (-sin(source.angle) * sign).toFloat()
                val len = max(10f, 0.6f * z)
                drawLine(
                    color,
                    Offset(c.x - dx * len * 0.5f, c.y - dy * len * 0.5f),
                    Offset(c.x + dx * len * 0.5f, c.y + dy * len * 0.5f),
                    1.6f,
                )
            }

            is MassSource -> {
                val c = screen(source.position)
                drawCircle(FieldGravity.copy(alpha = 0.16f), max(9f, 0.5f * z), c)
                drawCircle(FieldGravity, max(6f, 0.36f * z), c, style = Stroke(2f))
            }

            is Magnet -> {
                drawPreviewUniformRegion(source.position, source.extent, source.shape, FieldMagnetic, ::sx, ::sy, z)
                val step = 2.0
                val radius = min(5f, max(2f, 0.12f * z))
                var gx = -source.extent + 1.0
                while (gx <= source.extent - 0.5) {
                    var gy = -source.extent + 1.0
                    while (gy <= source.extent - 0.5) {
                        if (source.shape == FieldShape.CIRCLE &&
                            gx * gx + gy * gy > source.extent * source.extent
                        ) {
                            gy += step
                            continue
                        }
                        val c = screen(Vec2(source.position.x + gx, source.position.y + gy))
                        drawCircle(FieldMagnetic.copy(alpha = 0.7f), radius, c, style = Stroke(1f))
                        if (source.strength >= 0.0) {
                            drawCircle(FieldMagnetic.copy(alpha = 0.7f), radius * 0.28f, c)
                        } else {
                            val d = radius * 0.55f
                            drawLine(FieldMagnetic, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 1f)
                            drawLine(FieldMagnetic, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), 1f)
                        }
                        gy += step
                    }
                    gx += step
                }
            }

            else -> Unit
        }
    }

    for (body in bodies) {
        val c = screen(body.position)
        val r = (body.size * z).toFloat().coerceAtLeast(3f)
        val color = when {
            body.charge > 0.05 -> FieldElectric
            body.charge < -0.05 -> Danger
            else -> TextPrimary
        }
        if (body.shape == BodyShape.CIRCLE) {
            drawCircle(SurfaceRaised.copy(alpha = 0.6f), r, c)
            drawCircle(color, r, c, style = Stroke(1.8f))
        } else {
            rotate(Math.toDegrees(body.angle).toFloat(), c) {
                drawRect(SurfaceRaised.copy(alpha = 0.6f), c - Offset(r, r), Size(r * 2f, r * 2f))
                drawRect(color, c - Offset(r, r), Size(r * 2f, r * 2f), style = Stroke(1.8f))
            }
        }
    }

    for (probe in probes) {
        drawCircle(TextPrimary.copy(alpha = 0.8f), 1.8f, screen(probe.position))
    }
}

private fun DrawScope.drawPreviewUniformRegion(
    position: Vec2,
    extent: Double,
    shape: FieldShape,
    color: Color,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    if (shape == FieldShape.CIRCLE) {
        val c = Offset(sx(position.x), sy(position.y))
        val radius = extent.toFloat() * zoom
        drawCircle(color.copy(alpha = 0.08f), radius, c)
        drawCircle(color.copy(alpha = 0.45f), radius, c, style = Stroke(1.2f))
    } else {
        val topLeft = Offset(sx(position.x - extent), sy(position.y + extent))
        val size = Size((extent * 2.0).toFloat() * zoom, (extent * 2.0).toFloat() * zoom)
        drawRect(color.copy(alpha = 0.08f), topLeft, size)
        drawRect(color.copy(alpha = 0.45f), topLeft, size, style = Stroke(1.2f))
    }
}

private fun DrawScope.drawPreviewGrid(
    bounds: Aabb,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    width: Float,
    height: Float,
) {
    val step = when {
        zoom >= 48f -> 0.5
        zoom >= 16f -> 1.0
        zoom >= 7f -> 2.0
        else -> 5.0
    }
    val left = (0f - width / 2f) / zoom
    val right = (width - width / 2f) / zoom
    val top = height / 2f / zoom
    val bottom = (height / 2f - height) / zoom

    var x = ceil(left / step) * step
    var guard = 0
    while (x <= right && guard < 300) {
        drawLine(GridLine, Offset(sx(x), 0f), Offset(sx(x), height), 1f)
        x += step
        guard++
    }
    var y = ceil(bottom / step) * step
    guard = 0
    while (y <= top && guard < 300) {
        drawLine(GridLine, Offset(0f, sy(y)), Offset(width, sy(y)), 1f)
        y += step
        guard++
    }
    drawRect(
        color = GridLineMajor,
        topLeft = Offset(sx(bounds.minX), sy(bounds.maxY)),
        size = Size(bounds.width.toFloat() * zoom, bounds.height.toFloat() * zoom),
        style = Stroke(1.5f),
    )
}

private fun DrawScope.drawPreviewZone(
    min: Vec2,
    max: Vec2,
    shape: FieldShape,
    color: Color,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val center = Vec2((min.x + max.x) * 0.5, (min.y + max.y) * 0.5)
    if (shape == FieldShape.CIRCLE) {
        val radius = min((max.x - min.x) * 0.5, (max.y - min.y) * 0.5).toFloat() * zoom
        val c = Offset(sx(center.x), sy(center.y))
        drawCircle(color.copy(alpha = 0.10f), radius, c)
        drawCircle(color.copy(alpha = 0.45f), radius, c, style = Stroke(1.2f))
    } else {
        val topLeft = Offset(sx(min.x), sy(max.y))
        val size = Size((max.x - min.x).toFloat() * zoom, (max.y - min.y).toFloat() * zoom)
        drawRect(color.copy(alpha = 0.10f), topLeft, size)
        drawRect(color.copy(alpha = 0.45f), topLeft, size, style = Stroke(1.2f))
    }
}

private fun DrawScope.drawPreviewArrows(
    arrows: List<PreviewArrow>,
    bounds: Aabb,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    if (arrows.isEmpty()) return
    val maxMagnitude = arrows.maxOf { it.sample.magnitude }
    if (maxMagnitude <= 0.0) return
    val cell = min(bounds.width, bounds.height) / 16.0
    val logMax = Math.log1p(maxMagnitude)
    for (arrow in arrows) {
        val sample = arrow.sample
        val length = kotlin.math.sqrt(sample.vx * sample.vx + sample.vy * sample.vy)
        if (length < 1e-9) continue
        val strength = (Math.log1p(length) / logMax).coerceIn(0.0, 1.0)
        val pixelLength = (strength * cell * zoom * 0.5).toFloat().coerceIn(3f, cell.toFloat() * zoom)
        val dirX = (sample.vx / length).toFloat()
        val dirY = -(sample.vy / length).toFloat()
        val c = Offset(sx(sample.x), sy(sample.y))
        drawLine(
            arrow.color.copy(alpha = 0.20f + 0.30f * strength.toFloat()),
            Offset(c.x - dirX * pixelLength * 0.5f, c.y - dirY * pixelLength * 0.5f),
            Offset(c.x + dirX * pixelLength * 0.5f, c.y + dirY * pixelLength * 0.5f),
            1.2f,
        )
    }
}
