package dev.field.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
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
import dev.field.core.fields.FieldModel
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.scene.Scene
import dev.field.core.sim.BodyShape
import dev.field.core.viz.FieldViz
import dev.field.core.fields.FieldKind
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Read-only render of a saved scene, used for the menu backdrop and the save
 * preview. The camera is supplied by the caller, so the menu can use any
 * framing of a save without mutating it.
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

    Canvas(modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val z = zoom.toFloat()
        fun sx(x: Double) = width / 2f + (x - center.x).toFloat() * z
        fun sy(y: Double) = height / 2f - (y - center.y).toFloat() * z
        fun screen(p: Vec2) = Offset(sx(p.x), sy(p.y))

        drawRect(dev.field.app.ui.theme.Bg)
        drawPreviewGrid(scene.world.bounds, ::sx, ::sy, z, width, height)

        for (source in scene.sources) {
            when (source) {
                is GravityZone -> drawPreviewZone(source.min, source.max, FieldGravity, ::sx, ::sy, z)
                is ForceZone -> drawPreviewZone(source.min, source.max, FieldForce, ::sx, ::sy, z)
                is DragRegion -> drawPreviewZone(source.min, source.max, FieldDrag, ::sx, ::sy, z)
                else -> Unit
            }
        }

        if (showArrows) {
            drawPreviewArrows(field, scene.world.bounds, ::sx, ::sy, z)
        }

        for (source in scene.sources) {
            when (source) {
                is WallSegment -> drawLine(
                    TextPrimary.copy(alpha = 0.75f),
                    Offset(sx(source.start.x), sy(source.start.y)),
                    Offset(sx(source.end.x), sy(source.end.y)),
                    strokeWidth = max(2f, source.thickness.toFloat() * z),
                    cap = StrokeCap.Square,
                )

                is PointCharge -> {
                    val c = screen(source.position)
                    val color = if (source.charge >= 0.0) FieldElectric else Danger
                    drawCircle(color.copy(alpha = 0.20f), max(8f, 0.45f * z), c)
                    drawCircle(color, max(5f, 0.28f * z), c, style = Stroke(1.5f))
                }

                is MassSource -> {
                    val c = screen(source.position)
                    drawCircle(FieldGravity.copy(alpha = 0.16f), max(9f, 0.5f * z), c)
                    drawCircle(FieldGravity, max(6f, 0.36f * z), c, style = Stroke(2f))
                }

                is Magnet -> {
                    val a = source.angle
                    val n = screen(
                        Vec2(
                            source.position.x + cos(a) * Magnet.POLE_OFFSET,
                            source.position.y + sin(a) * Magnet.POLE_OFFSET,
                        ),
                    )
                    val s = screen(
                        Vec2(
                            source.position.x - cos(a) * Magnet.POLE_OFFSET,
                            source.position.y - sin(a) * Magnet.POLE_OFFSET,
                        ),
                    )
                    drawLine(FieldMagnetic.copy(alpha = 0.8f), s, n, strokeWidth = 2f)
                    drawCircle(Danger, 3.5f, n)
                    drawCircle(FieldElectric, 3.5f, s)
                }

                else -> Unit
            }
        }

        for (body in scene.bodies) {
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

        for (probe in scene.probes) {
            drawCircle(TextPrimary.copy(alpha = 0.8f), 1.8f, screen(probe.position))
        }
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
    color: Color,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val topLeft = Offset(sx(min.x), sy(max.y))
    val size = Size((max.x - min.x).toFloat() * zoom, (max.y - min.y).toFloat() * zoom)
    drawRect(color.copy(alpha = 0.10f), topLeft, size)
    drawRect(color.copy(alpha = 0.45f), topLeft, size, style = Stroke(1.2f))
}

private fun DrawScope.drawPreviewArrows(
    field: FieldModel,
    bounds: Aabb,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val kinds = mutableListOf<Pair<FieldKind, Color>>()
    if (field.sources.any { it is PointCharge }) kinds.add(FieldKind.ELECTRIC to FieldElectric)
    if (field.sources.any { it is Magnet }) kinds.add(FieldKind.MAGNETIC to FieldMagnetic)
    if (field.sources.any { it is MassSource || it is GravityZone }) kinds.add(FieldKind.GRAVITY to FieldGravity)
    if (field.sources.any { it is ForceZone }) kinds.add(FieldKind.UNIFORM_FORCE to FieldForce)
    if (field.sources.any { it is DragRegion }) kinds.add(FieldKind.DRAG to FieldDrag)

    val cell = min(bounds.width, bounds.height) / 16.0
    for ((kind, color) in kinds) {
        val samples = FieldViz.arrows(field, bounds, cols = 20, rows = 20, kind = kind)
        val maxMagnitude = samples.maxOfOrNull { it.magnitude } ?: 0.0
        if (maxMagnitude <= 0.0) continue
        val logMax = Math.log1p(maxMagnitude)
        for (sample in samples) {
            val length = kotlin.math.sqrt(sample.vx * sample.vx + sample.vy * sample.vy)
            if (length < 1e-9) continue
            val strength = (Math.log1p(length) / logMax).coerceIn(0.0, 1.0)
            val pixelLength = (strength * cell * zoom * 0.5).toFloat().coerceIn(3f, cell.toFloat() * zoom)
            val dirX = (sample.vx / length).toFloat()
            val dirY = -(sample.vy / length).toFloat()
            val c = Offset(sx(sample.x), sy(sample.y))
            drawLine(
                color.copy(alpha = 0.20f + 0.30f * strength.toFloat()),
                Offset(c.x - dirX * pixelLength * 0.5f, c.y - dirY * pixelLength * 0.5f),
                Offset(c.x + dirX * pixelLength * 0.5f, c.y + dirY * pixelLength * 0.5f),
                1.2f,
            )
        }
    }
}
