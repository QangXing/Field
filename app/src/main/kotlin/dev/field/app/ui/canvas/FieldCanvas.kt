package dev.field.app.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.editor.EditorState
import dev.field.app.editor.EditorTool
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Border
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.FieldDrag
import dev.field.app.ui.theme.FieldElectric
import dev.field.app.ui.theme.FieldForce
import dev.field.app.ui.theme.FieldGravity
import dev.field.app.ui.theme.FieldMagnetic
import dev.field.app.ui.theme.GridLine
import dev.field.app.ui.theme.GridLineMajor
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.fields.DragRegion
import dev.field.core.fields.FieldKind
import dev.field.core.fields.FieldModel
import dev.field.core.fields.FieldSource
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.viz.ArrowSample
import dev.field.core.viz.FieldViz
import dev.field.core.viz.HeatmapMode
import kotlin.math.ceil
import kotlin.math.floor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class GhostItem(val source: FieldSource, val valid: Boolean)

private data class ArrowLayer(
    val color: Color,
    val samples: List<ArrowSample>,
    val maxMagnitude: Double,
)

private data class StreamLayer(val color: Color, val lines: List<List<Vec2>>)

@Composable
fun FieldCanvas(state: EditorState, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ghosts by remember { mutableStateOf<List<GhostItem>>(emptyList()) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    @Suppress("UNUSED_VARIABLE")
    val frameTick = state.tick

    val snapshot = state.sources.toList()
    val fieldModel = remember(snapshot, state.globalGravity) {
        FieldModel(snapshot, state.globalGravity)
    }

    val arrowLayers = remember(fieldModel, state.bounds) {
        buildArrowLayers(fieldModel, state.bounds)
    }
    val streamLayers = remember(fieldModel, state.bounds) {
        buildStreamLayers(fieldModel, state.bounds)
    }
    val heatmapColor = remember(fieldModel, state.bounds, state.visuals.heatmapMode, state.visuals.heatmap) {
        if (state.visuals.heatmap) {
            buildHeatmap(fieldModel, state.bounds, state.visuals.heatmapMode)
        } else {
            null
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(state.tool, state.selectedId, state.settings.gridSnap, canvasSize) {
                val width = canvasSize.width.toFloat()
                val height = canvasSize.height.toFloat()
                if (width <= 0f || height <= 0f) return@pointerInput

                suspend fun androidx.compose.ui.input.pointer.PointerInputScope.gestures() {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = down.position
                        var lastPosition = downPosition
                        var totalDrag = 0f
                        var multiTouch = false
                        var undoPushed = false
                        var eraseUndoPushed = false
                        val dragStartWorld = state.toWorld(downPosition, width, height)
                        var lastWorld = dragStartWorld
                        val hit = if (state.tool == null) state.hitTest(lastWorld) else null

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }

                            if (pressed >= 2) {
                                multiTouch = true
                                ghosts = emptyList()
                                val zoomChange = event.calculateZoom()
                                if (zoomChange.isFinite() && zoomChange != 1f) {
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val anchor = state.toWorld(centroid, width, height)
                                    val newZoom = (state.zoom * zoomChange).coerceIn(4.0, 160.0)
                                    state.zoom = newZoom
                                    state.cameraCenter = Vec2(
                                        anchor.x - (centroid.x - width / 2f) / newZoom,
                                        anchor.y + (centroid.y - height / 2f) / newZoom,
                                    )
                                }
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) {
                                    state.cameraCenter = state.cameraCenter + Vec2(
                                        -pan.x / state.zoom,
                                        pan.y / state.zoom,
                                    )
                                }
                            } else {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null && change.pressed) {
                                    val delta = change.position - lastPosition
                                    totalDrag += delta.getDistance()
                                    val currentWorld = state.toWorld(change.position, width, height)
                                    if (totalDrag > viewConfiguration.touchSlop) {
                                        when {
                                            state.tool == null -> {
                                                if (hit != null) {
                                                    if (!undoPushed) {
                                                        state.pushUndo()
                                                        undoPushed = true
                                                    }
                                                    state.moveSourceBy(
                                                        hit.id,
                                                        Vec2(delta.x / state.zoom, -delta.y / state.zoom),
                                                    )
                                                } else {
                                                    state.cameraCenter = state.cameraCenter + Vec2(
                                                        -delta.x / state.zoom,
                                                        delta.y / state.zoom,
                                                    )
                                                }
                                            }

                                            state.tool?.isEraser == true -> {
                                                val erased = state.eraseAt(
                                                    currentWorld,
                                                    recordUndo = !eraseUndoPushed,
                                                )
                                                if (erased > 0) eraseUndoPushed = true
                                            }

                                            else -> {
                                                val active = state.tool ?: continue
                                                val positions = state.linePositions(dragStartWorld, currentWorld)
                                                ghosts = positions.mapNotNull { position ->
                                                    val source = state.makeSource(active, position)
                                                    source?.let {
                                                        GhostItem(it, state.validate(it) == null)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    lastPosition = change.position
                                    lastWorld = currentWorld
                                }
                            }

                            if (event.changes.all { !it.pressed }) break
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }

                        if (!multiTouch) {
                            if (totalDrag <= viewConfiguration.touchSlop) {
                                val world = state.toWorld(downPosition, width, height)
                                when {
                                    state.tool == null -> state.select(state.hitTest(world)?.id)
                                    state.tool?.isEraser == true -> state.eraseAt(world)
                                    else -> state.placeAt(world)
                                }
                            } else if (state.tool != null && !state.tool!!.isEraser) {
                                state.placeLine(
                                    dragStartWorld,
                                    state.toWorld(lastPosition, width, height),
                                )
                            }
                        }
                        ghosts = emptyList()
                    }
                }

                gestures()
            },
    ) {
        val zoom = state.zoom.toFloat()
        val center = state.cameraCenter
        val width = size.width
        val height = size.height

        fun sx(x: Double) = width / 2f + (x - center.x).toFloat() * zoom
        fun sy(y: Double) = height / 2f - (y - center.y).toFloat() * zoom
        fun screen(p: Vec2) = Offset(sx(p.x), sy(p.y))
        fun screenSize(sizeMeters: Vec2) = Size(
            sizeMeters.x.toFloat() * zoom,
            sizeMeters.y.toFloat() * zoom,
        )

        drawRect(Bg)

        drawGrid(state.bounds, ::sx, ::sy, zoom, width, height)

        heatmapColor?.let { image ->
            val topLeft = screen(Vec2(state.bounds.minX, state.bounds.maxY))
            drawImage(
                image = image,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(
                    (state.bounds.width * zoom).roundToInt(),
                    (state.bounds.height * zoom).roundToInt(),
                ),
                filterQuality = FilterQuality.None,
            )
        }

        if (state.visuals.streamlines) {
            for (layer in streamLayers) {
                for (line in layer.lines) {
                    if (line.size < 2) continue
                    val path = Path()
                    val first = screen(line.first())
                    path.moveTo(first.x, first.y)
                    for (index in 1 until line.size) {
                        val point = screen(line[index])
                        path.lineTo(point.x, point.y)
                    }
                    drawPath(path, layer.color.copy(alpha = 0.42f), style = Stroke(width = 1.2f))
                }
            }
        }

        for (source in state.sources) {
            drawZone(source, ::sx, ::sy, zoom)
        }

        for (source in state.sources) {
            if (source is WallSegment) drawWall(source, ::sx, ::sy, zoom)
        }

        if (state.visuals.arrows) {
            for (layer in arrowLayers) {
                drawArrowLayer(layer, state.bounds, ::sx, ::sy, zoom)
            }
        }

        for (source in state.sources) {
            drawSource(source, textMeasurer, ::sx, ::sy, zoom, selected = source.id == state.selectedId)
        }

        drawProbes(state, ::sx, ::sy, zoom, density.density)

        for (ghost in ghosts) {
            drawGhost(ghost, textMeasurer, ::sx, ::sy, zoom)
        }
    }
}

// ----------------------------------------------------------------------- drawing

private fun DrawScope.drawGrid(
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

    var startX = ceil((left * 1.0) / step) * step
    var guard = 0
    while (startX <= right && guard < 300) {
        val screenX = sx(startX)
        val major = (startX / (step * 5.0)).roundToInt()
        val isMajor = kotlin.math.abs(startX / (step * 5.0) - major) < 1e-6
        drawLine(
            color = if (isMajor) GridLineMajor else GridLine,
            start = Offset(screenX, 0f),
            end = Offset(screenX, height),
            strokeWidth = 1f,
        )
        startX += step
        guard++
    }

    var startY = ceil((bottom * 1.0) / step) * step
    guard = 0
    while (startY <= top && guard < 300) {
        val screenY = sy(startY)
        drawLine(
            color = if (((startY / (step * 5.0)).roundToInt()).let {
                    kotlin.math.abs(startY / (step * 5.0) - it) < 1e-6
                }
            ) {
                GridLineMajor
            } else {
                GridLine
            },
            start = Offset(0f, screenY),
            end = Offset(width, screenY),
            strokeWidth = 1f,
        )
        startY += step
        guard++
    }

    val topLeft = Offset(sx(bounds.minX), sy(bounds.maxY))
    drawRect(
        color = Border,
        topLeft = topLeft,
        size = Size(
            bounds.width.toFloat() * zoom,
            bounds.height.toFloat() * zoom,
        ),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawZone(
    source: FieldSource,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val (min, max, color) = when (source) {
        is GravityZone -> Triple(source.min, source.max, FieldGravity)
        is ForceZone -> Triple(source.min, source.max, FieldForce)
        is DragRegion -> Triple(source.min, source.max, FieldDrag)
        else -> return
    }
    val topLeft = Offset(sx(min.x), sy(max.y))
    val size = Size((max.x - min.x).toFloat() * zoom, (max.y - min.y).toFloat() * zoom)
    drawRect(color.copy(alpha = 0.10f), topLeft, size)
    drawRect(color.copy(alpha = 0.55f), topLeft, size, style = Stroke(width = 1.5f))

    if (source is DragRegion) {
        clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
            var offset = -size.height
            while (offset < size.width) {
                drawLine(
                    color = color.copy(alpha = 0.18f),
                    start = Offset(topLeft.x + offset, topLeft.y + size.height),
                    end = Offset(topLeft.x + offset + size.height, topLeft.y),
                    strokeWidth = 1f,
                )
                offset += 8f
            }
        }
    }
}

private fun DrawScope.drawWall(
    wall: WallSegment,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    drawLine(
        color = TextPrimary.copy(alpha = 0.75f),
        start = Offset(sx(wall.start.x), sy(wall.start.y)),
        end = Offset(sx(wall.end.x), sy(wall.end.y)),
        strokeWidth = max(2f, wall.thickness.toFloat() * zoom),
        cap = StrokeCap.Square,
    )
}

private fun DrawScope.drawSource(
    source: FieldSource,
    textMeasurer: TextMeasurer,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    selected: Boolean,
) {
    when (source) {
        is PointCharge -> {
            val center = Offset(sx(source.position.x), sy(source.position.y))
            val color = if (source.charge >= 0.0) FieldElectric else Danger
            drawCircle(color.copy(alpha = 0.18f), radius = max(10f, 0.45f * zoom), center = center)
            drawCircle(color, radius = max(7f, 0.3f * zoom), center = center)
            drawCircle(color.copy(alpha = 0.9f), radius = max(7f, 0.3f * zoom), center = center, style = Stroke(1.2f))
            drawText(
                textMeasurer,
                if (source.charge >= 0.0) "+" else "−",
                topLeft = Offset(center.x - 4.dp.toPx(), center.y - 8.dp.toPx()),
                style = TextStyle(color = Bg, fontSize = 12.sp),
            )
            if (selected) drawCircle(Accent, max(13f, 0.55f * zoom), center, style = Stroke(2f))
        }

        is MassSource -> {
            val center = Offset(sx(source.position.x), sy(source.position.y))
            drawCircle(FieldGravity.copy(alpha = 0.18f), max(12f, 0.5f * zoom), center)
            drawCircle(FieldGravity, max(8f, 0.4f * zoom), center, style = Stroke(2.5f))
            drawCircle(FieldGravity, max(2f, 0.08f * zoom), center)
            drawText(
                textMeasurer,
                "M",
                topLeft = Offset(center.x + 0.55f * zoom, center.y - 0.75f * zoom),
                style = TextStyle(color = FieldGravity, fontSize = 11.sp),
            )
            if (selected) drawCircle(Accent, max(15f, 0.6f * zoom), center, style = Stroke(2f))
        }

        is Magnet -> {
            val center = Offset(sx(source.position.x), sy(source.position.y))
            val angle = source.angle
            val northX = source.position.x + kotlin.math.cos(angle) * Magnet.POLE_OFFSET
            val northY = source.position.y + kotlin.math.sin(angle) * Magnet.POLE_OFFSET
            val southX = source.position.x - kotlin.math.cos(angle) * Magnet.POLE_OFFSET
            val southY = source.position.y - kotlin.math.sin(angle) * Magnet.POLE_OFFSET
            val north = Offset(sx(northX), sy(northY))
            val south = Offset(sx(southX), sy(southY))
            drawLine(FieldMagnetic.copy(alpha = 0.8f), south, north, strokeWidth = 3f)
            drawCircle(Danger, 5f, north)
            drawCircle(FieldElectric, 5f, south)
            drawText(
                textMeasurer,
                "N",
                topLeft = Offset(north.x + 5f, north.y - 14f),
                style = TextStyle(color = Danger, fontSize = 9.sp),
            )
            drawText(
                textMeasurer,
                "S",
                topLeft = Offset(south.x + 5f, south.y + 1f),
                style = TextStyle(color = FieldElectric, fontSize = 9.sp),
            )
            if (selected) drawCircle(Accent, max(16f, 0.7f * zoom), center, style = Stroke(2f))
        }

        is WallSegment -> {
            // Drawn in a dedicated pass so walls stay above zone fills.
        }

        else -> Unit
    }
}

private fun DrawScope.drawArrowLayer(
    layer: ArrowLayer,
    bounds: Aabb,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    if (layer.samples.isEmpty() || layer.maxMagnitude <= 0.0) return
    val cell = min(bounds.width, bounds.height) / 14.0
    val logMax = Math.log1p(layer.maxMagnitude)

    for (sample in layer.samples) {
        val length = sqrt(sample.vx * sample.vx + sample.vy * sample.vy)
        if (length < 1e-9) continue
        val strength = (Math.log1p(length) / logMax).coerceIn(0.0, 1.0)
        val pixelLength = (strength * cell * zoom * 0.55).toFloat().coerceIn(4f, cell.toFloat() * zoom)
        val dirX = (sample.vx / length).toFloat()
        val dirY = -(sample.vy / length).toFloat()
        val center = Offset(sx(sample.x), sy(sample.y))
        val start = Offset(
            center.x - dirX * pixelLength * 0.5f,
            center.y - dirY * pixelLength * 0.5f,
        )
        val end = Offset(
            center.x + dirX * pixelLength * 0.5f,
            center.y + dirY * pixelLength * 0.5f,
        )
        drawLine(layer.color.copy(alpha = 0.35f + 0.35f * strength.toFloat()), start, end, 1.4f)
        val head = pixelLength * 0.28f
        val perpX = -dirY
        val perpY = dirX
        drawLine(
            layer.color.copy(alpha = 0.6f),
            end,
            Offset(end.x - dirX * head + perpX * head * 0.55f, end.y - dirY * head + perpY * head * 0.55f),
            1.4f,
        )
        drawLine(
            layer.color.copy(alpha = 0.6f),
            end,
            Offset(end.x - dirX * head - perpX * head * 0.55f, end.y - dirY * head - perpY * head * 0.55f),
            1.4f,
        )
    }
}

private fun DrawScope.drawProbes(
    state: EditorState,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    density: Float,
) {
    if (state.visuals.trails) {
        val trails = state.engine.probes
        val drawTrails = trails.size <= 900
        for (probe in trails) {
            val trail = probe.trail
            if (trail.size < 2) continue
            if (drawTrails) {
                for (index in 1 until trail.size) {
                    val alpha = 0.06f + 0.5f * (index.toFloat() / trail.size)
                    drawLine(
                        color = if (probe.faulted) Danger.copy(alpha = alpha) else FieldElectric.copy(alpha = alpha),
                        start = Offset(sx(trail[index - 1].x), sy(trail[index - 1].y)),
                        end = Offset(sx(trail[index].x), sy(trail[index].y)),
                        strokeWidth = 1.4f,
                    )
                }
            } else {
                val last = trail.last()
                val first = trail.first()
                drawLine(
                    FieldElectric.copy(alpha = 0.25f),
                    Offset(sx(first.x), sy(first.y)),
                    Offset(sx(last.x), sy(last.y)),
                    strokeWidth = 1f,
                )
            }
        }
    }

    val points = ArrayList<Offset>(state.engine.probes.size)
    val faulted = ArrayList<Offset>()
    for (probe in state.engine.probes) {
        val offset = Offset(sx(probe.position.x), sy(probe.position.y))
        if (probe.faulted) faulted.add(offset) else points.add(offset)
    }
    val radius = max(2.2f, 0.12f * zoom).toFloat()
    if (points.isNotEmpty()) {
        drawPoints(points, PointMode.Points, TextPrimary, strokeWidth = radius * 2f, cap = StrokeCap.Round)
    }
    if (faulted.isNotEmpty()) {
        drawPoints(faulted, PointMode.Points, Danger, strokeWidth = radius * 2.4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawGhost(
    ghost: GhostItem,
    textMeasurer: TextMeasurer,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val color = if (ghost.valid) Accent else Danger
    val source = ghost.source
    when (source) {
        is PointCharge, is MassSource, is Magnet -> {
            val point = when (source) {
                is PointCharge -> source.position
                is MassSource -> source.position
                is Magnet -> source.position
                else -> return
            }
            val center = Offset(sx(point.x), sy(point.y))
            val radius = if (source is Magnet) max(14f, 0.7f * zoom) else max(12f, 0.45f * zoom)
            drawCircle(color.copy(alpha = 0.16f), radius, center)
            drawCircle(color, radius, center, style = Stroke(2f))
            drawText(
                textMeasurer,
                if (source is PointCharge && source.charge >= 0.0) "+" else
                    if (source is PointCharge) "−" else "·",
                topLeft = Offset(center.x - 4.dp.toPx(), center.y - 8.dp.toPx()),
                style = TextStyle(color = color, fontSize = 12.sp),
            )
        }

        is WallSegment -> {
            drawLine(
                color,
                Offset(sx(source.start.x), sy(source.start.y)),
                Offset(sx(source.end.x), sy(source.end.y)),
                strokeWidth = max(2f, source.thickness.toFloat() * zoom),
                cap = StrokeCap.Square,
            )
        }

        else -> {
            val (min, max) = when (source) {
                is GravityZone -> source.min to source.max
                is ForceZone -> source.min to source.max
                is DragRegion -> source.min to source.max
                else -> return
            }
            val topLeft = Offset(sx(min.x), sy(max.y))
            val zoneSize = Size((max.x - min.x).toFloat() * zoom, (max.y - min.y).toFloat() * zoom)
            drawRect(color.copy(alpha = 0.14f), topLeft, zoneSize)
            drawRect(color, topLeft, zoneSize, style = Stroke(2f))
        }
    }
}

// ----------------------------------------------------------------------- viz prep

private fun buildArrowLayers(field: FieldModel, bounds: Aabb): List<ArrowLayer> {
    val hasElectric = field.sources.any { it is PointCharge }
    val hasMagnetic = field.sources.any { it is Magnet }
    val hasGravity = field.sources.any { it is MassSource || it is GravityZone }
    val hasDrag = field.sources.any { it is DragRegion }
    val hasForce = field.sources.any { it is ForceZone }

    val layers = ArrayList<ArrowLayer>(5)
    if (hasElectric) {
        addLayer(layers, field, bounds, FieldKind.ELECTRIC, FieldElectric)
    }
    if (hasMagnetic) {
        addLayer(layers, field, bounds, FieldKind.MAGNETIC, FieldMagnetic)
    }
    if (hasGravity) {
        addLayer(layers, field, bounds, FieldKind.GRAVITY, FieldGravity)
    }
    if (hasDrag) {
        addLayer(layers, field, bounds, FieldKind.DRAG, FieldDrag)
    }
    if (hasForce) {
        addLayer(layers, field, bounds, FieldKind.UNIFORM_FORCE, FieldForce)
    }
    return layers
}

private fun addLayer(
    layers: MutableList<ArrowLayer>,
    field: FieldModel,
    bounds: Aabb,
    kind: FieldKind,
    color: Color,
) {
    val samples = FieldViz.arrows(field, bounds, cols = 24, rows = 14, kind = kind)
    val maxMagnitude = samples.maxOfOrNull { it.magnitude } ?: 0.0
    layers.add(ArrowLayer(color, samples, maxMagnitude))
}

private fun buildStreamLayers(field: FieldModel, bounds: Aabb): List<StreamLayer> {
    val layers = ArrayList<StreamLayer>(2)
    if (field.sources.any { it is PointCharge }) {
        val seeds = FieldViz.defaultSeeds(field, FieldKind.ELECTRIC, bounds)
        layers.add(
            StreamLayer(
                FieldElectric,
                FieldViz.streamlines(field, FieldKind.ELECTRIC, seeds, bounds, maxSteps = 200, step = 0.18),
            ),
        )
    }
    if (field.sources.any { it is Magnet }) {
        val seeds = FieldViz.defaultSeeds(field, FieldKind.MAGNETIC, bounds)
        layers.add(
            StreamLayer(
                FieldMagnetic,
                FieldViz.streamlines(field, FieldKind.MAGNETIC, seeds, bounds, maxSteps = 200, step = 0.18),
            ),
        )
    }
    return layers
}

private fun buildHeatmap(field: FieldModel, bounds: Aabb, mode: HeatmapMode): ImageBitmap? {
    val cols = 96
    val rows = 54
    val heatmap = FieldViz.heatmap(field, bounds, cols, rows, mode)
    if (heatmap.values.isEmpty()) return null
    val pixels = IntArray(cols * rows)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val sourceIndex = row * cols + col
            val targetRow = rows - 1 - row
            pixels[targetRow * cols + col] = heatmapColor(heatmap.values[sourceIndex], mode).toArgb()
        }
    }
    val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
    return bitmap.asImageBitmap()
}

private fun EditorState.toWorld(position: Offset, width: Float, height: Float): Vec2 = Vec2(
    cameraCenter.x + (position.x - width / 2f) / zoom,
    cameraCenter.y - (position.y - height / 2f) / zoom,
)

private fun heatmapColor(value: Float, mode: HeatmapMode): Color {
    val v = value.coerceIn(0f, 1f)
    return when (mode) {
        HeatmapMode.POTENTIAL -> {
            if (v < 0.5f) {
                val t = v * 2f
                Color(
                    red = 0.08f + 0.10f * t,
                    green = 0.10f + 0.32f * t,
                    blue = 0.18f + 0.52f * t,
                    alpha = 0.55f,
                )
            } else {
                val t = (v - 0.5f) * 2f
                Color(
                    red = 0.18f + 0.72f * t,
                    green = 0.42f + 0.30f * t,
                    blue = 0.70f - 0.22f * t,
                    alpha = 0.55f,
                )
            }
        }

        HeatmapMode.MAGNITUDE -> Color(
            red = 0.10f + 0.90f * v,
            green = 0.10f + 0.56f * v,
            blue = 0.10f + 0.20f * v,
            alpha = 0.18f + 0.45f * v,
        )
    }
}
