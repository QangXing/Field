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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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
import dev.field.app.ui.theme.SurfaceRaised
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
import dev.field.core.sim.Body
import dev.field.core.sim.BodyShape
import dev.field.core.viz.ArrowSample
import dev.field.core.viz.FieldViz
import dev.field.core.viz.HeatmapMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private data class GhostItem(val source: FieldSource?, val body: Body?, val valid: Boolean)

private data class ArrowLayer(
    val color: Color,
    val samples: List<ArrowSample>,
    val maxMagnitude: Double,
)

private data class StreamLayer(val color: Color, val lines: List<List<Vec2>>, val alpha: Float = 0.42f)

private enum class DragMode { NONE, PAN, PLACE, ERASE, MOVE_SOURCE, MOVE_BODY, RESIZE_SOURCE }

@Composable
fun FieldCanvas(state: EditorState, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ghosts by remember { mutableStateOf<List<GhostItem>>(emptyList()) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val handleRadius = with(density) { 26.dp.toPx() }

    val snapshot = state.sources.toList()
    val bodyTick = state.bodies.size
    val fieldModel = remember(snapshot, state.globalGravity, state.rules.gravityEnabled) {
        FieldModel(snapshot, state.globalGravity, gravityEnabled = state.rules.gravityEnabled)
    }

    val arrowLayers = remember(fieldModel, state.bounds, state.rules.fieldArrows, state.visuals.arrows) {
        if (state.rules.fieldArrows && state.visuals.arrows) {
            buildArrowLayers(fieldModel, state.bounds)
        } else {
            emptyList()
        }
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
            .pointerInput(
                state.tool,
                state.selectedId,
                state.selectedBodyId,
                state.settings.gridSnap,
                state.display.lockCamera,
                canvasSize,
                bodyTick,
            ) {
                val width = canvasSize.width.toFloat()
                val height = canvasSize.height.toFloat()
                if (width <= 0f || height <= 0f) return@pointerInput

                fun worldOf(position: Offset): Vec2 = state.toWorld(position, width, height)

                fun screenOf(point: Vec2): Offset = Offset(
                    width / 2f + (point.x - state.cameraCenter.x).toFloat() * state.zoom.toFloat(),
                    height / 2f - (point.y - state.cameraCenter.y).toFloat() * state.zoom.toFloat(),
                )

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    val downWorld = worldOf(downPosition)
                    var lastPosition = downPosition
                    var totalDrag = 0f
                    var multiTouch = false
                    var undoPushed = false
                    val dragStartWorld = downWorld

                    val selected = state.selectedSource
                    val frame = selected?.let { state.sourceFrame(it) }
                    val frameCenter = frame?.let { screenOf(it.center) }
                    val cornerPoint = frame?.let {
                        Vec2(it.center.x + it.halfWidth, it.center.y + it.halfHeight)
                    }
                    val frameCorner = cornerPoint?.let { screenOf(it) }
                    val nearCenter = frameCenter != null &&
                        (downPosition - frameCenter).getDistance() <= handleRadius
                    val nearCorner = frameCorner != null &&
                        (downPosition - frameCorner).getDistance() <= handleRadius
                    val nearEdge = if (frame != null) {
                        val topLeft = screenOf(Vec2(frame.center.x - frame.halfWidth, frame.center.y + frame.halfHeight))
                        val right = screenOf(Vec2(frame.center.x + frame.halfWidth, frame.center.y))
                        val bottom = screenOf(Vec2(frame.center.x, frame.center.y - frame.halfHeight))
                        val edgeTolerance = handleRadius * 0.8f
                        val withinX = downPosition.x >= topLeft.x - edgeTolerance &&
                            downPosition.x <= right.x + edgeTolerance
                        val withinY = downPosition.y >= topLeft.y - edgeTolerance &&
                            downPosition.y <= bottom.y + edgeTolerance
                        (withinX && (abs(downPosition.y - topLeft.y) <= edgeTolerance ||
                            abs(downPosition.y - bottom.y) <= edgeTolerance)) ||
                            (withinY && (abs(downPosition.x - topLeft.x) <= edgeTolerance ||
                                abs(downPosition.x - right.x) <= edgeTolerance))
                    } else {
                        false
                    }
                    val bodyHit = state.hitTestBody(downWorld)
                    val sourceHit = state.hitTest(downWorld)

                    var mode = when {
                        state.tool?.isEraser == true -> DragMode.ERASE
                        state.tool == null && nearCorner -> DragMode.RESIZE_SOURCE
                        state.tool == null && nearEdge -> DragMode.RESIZE_SOURCE
                        state.tool == null && nearCenter -> DragMode.MOVE_SOURCE

                        state.tool == null && bodyHit != null -> DragMode.MOVE_BODY
                        state.tool == null && sourceHit != null -> DragMode.MOVE_SOURCE
                        state.tool == null -> DragMode.PAN
                        else -> DragMode.PLACE
                    }

                    when (mode) {
                        DragMode.MOVE_BODY -> bodyHit?.let { state.selectBody(it.id) }
                        DragMode.MOVE_SOURCE -> {
                            if (sourceHit != null) state.select(sourceHit.id)
                        }

                        else -> Unit
                    }

                    val resizeId = when {
                        mode == DragMode.RESIZE_SOURCE -> selected?.id
                        else -> null
                    }
                    val moveSourceId = when {
                        mode == DragMode.MOVE_SOURCE -> state.selectedId
                        else -> null
                    }
                    val moveBodyId = when {
                        mode == DragMode.MOVE_BODY -> state.selectedBodyId
                        else -> null
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }

                        if (pressed >= 2) {
                            multiTouch = true
                            ghosts = emptyList()
                            mode = DragMode.NONE
                            if (!state.display.lockCamera) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange.isFinite() && zoomChange != 1f) {
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val anchor = worldOf(centroid)
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
                            }
                        } else if (mode != DragMode.NONE) {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                val delta = change.position - lastPosition
                                totalDrag += delta.getDistance()
                                val currentWorld = worldOf(change.position)
                                val dragged = totalDrag > viewConfiguration.touchSlop
                                if (dragged) {
                                    when (mode) {
                                        DragMode.RESIZE_SOURCE -> {
                                            val id = resizeId
                                            if (id != null) {
                                                if (!undoPushed) {
                                                    state.pushUndo()
                                                    undoPushed = true
                                                }
                                                val current = state.sources.firstOrNull { it.id == id }
                                                val center = current?.let { state.sourceFrame(it) }?.center ?: currentWorld
                                                val hw = max(abs(state.snap(currentWorld.x) - center.x), 0.5)
                                                val hh = max(abs(state.snap(currentWorld.y) - center.y), 0.5)
                                                state.resizeSource(id, hw, hh)
                                            }
                                        }

                                        DragMode.MOVE_SOURCE -> {
                                            val id = moveSourceId
                                            if (id != null) {
                                                if (!undoPushed) {
                                                    state.pushUndo()
                                                    undoPushed = true
                                                }
                                                state.moveSourceBy(
                                                    id,
                                                    Vec2(delta.x / state.zoom, -delta.y / state.zoom),
                                                )
                                            }
                                        }

                                        DragMode.MOVE_BODY -> {
                                            val id = moveBodyId
                                            if (id != null) {
                                                state.moveBodyBy(
                                                    id,
                                                    Vec2(delta.x / state.zoom, -delta.y / state.zoom),
                                                )
                                            }
                                        }

                                        DragMode.PAN -> {
                                            if (!state.display.lockCamera) {
                                                state.cameraCenter = state.cameraCenter + Vec2(
                                                    -delta.x / state.zoom,
                                                    delta.y / state.zoom,
                                                )
                                            }
                                        }

                                        DragMode.ERASE -> {
                                            if (!undoPushed &&
                                                (state.hitTestBody(currentWorld) != null ||
                                                    state.hitTest(currentWorld) != null)
                                            ) {
                                                state.pushUndo()
                                                undoPushed = true
                                            }
                                            state.eraseAt(currentWorld, recordUndo = false)
                                        }

                                        DragMode.PLACE -> {
                                            val active = state.tool ?: continue
                                            val positions = state.linePositions(dragStartWorld, currentWorld)
                                            ghosts = positions.mapNotNull { position ->
                                                if (active.isBody) {
                                                    state.makeBody(active, position)?.let {
                                                        GhostItem(null, it, state.validateBody(it) == null)
                                                    }
                                                } else {
                                                    state.makeSource(active, position)?.let {
                                                        GhostItem(it, null, state.validate(it) == null)
                                                    }
                                                }
                                            }
                                        }

                                        DragMode.NONE -> Unit
                                    }
                                }
                                lastPosition = change.position
                            }
                        }

                        if (event.changes.all { !it.pressed }) break
                        event.changes.forEach { change ->
                            if (change.positionChanged()) change.consume()
                        }
                    }

                    if (!multiTouch) {
                        val endWorld = worldOf(lastPosition)
                        if (totalDrag <= viewConfiguration.touchSlop) {
                            when {
                                state.tool == null -> {
                                    val body = state.hitTestBody(downWorld)
                                    val source = state.hitTest(downWorld)
                                    when {
                                        body != null -> state.selectBody(body.id)
                                        source != null -> state.select(source.id)
                                        else -> {
                                            state.select(null)
                                            state.selectBody(null)
                                        }
                                    }
                                }

                                state.tool?.isEraser == true -> state.eraseAt(downWorld)
                                else -> state.placeAt(downWorld)
                            }
                        } else if (state.tool != null && !state.tool!!.isEraser) {
                            state.placeLine(dragStartWorld, endWorld)
                        }
                    }
                    ghosts = emptyList()
                }
            },
    ) {
        // Reading the frame tick and interpolation alpha inside the draw lambda
        // invalidates drawing only; it avoids recomposing the whole sandbox UI at 60 Hz.
        @Suppress("UNUSED_VARIABLE")
        val frameTick = state.tick
        @Suppress("UNUSED_VARIABLE")
        val alpha = state.renderAlpha

        val zoom = state.zoom.toFloat()
        val center = state.cameraCenter
        val width = size.width
        val height = size.height

        fun sx(x: Double) = width / 2f + (x - center.x).toFloat() * zoom
        fun sy(y: Double) = height / 2f - (y - center.y).toFloat() * zoom
        fun screen(p: Vec2) = Offset(sx(p.x), sy(p.y))

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
                    drawPath(path, layer.color.copy(alpha = layer.alpha), style = Stroke(width = 1.2f))
                }
            }
        }

        for (source in state.sources) {
            drawZone(source, ::sx, ::sy, zoom)
        }

        for (source in state.sources) {
            if (source is WallSegment) drawWall(source, ::sx, ::sy, zoom)
        }

        for (layer in arrowLayers) {
            drawArrowLayer(layer, state.bounds, ::sx, ::sy, zoom)
        }

        for (source in state.sources) {
            drawSource(source, textMeasurer, ::sx, ::sy, zoom, selected = source.id == state.selectedId)
        }

        state.selectedSource?.let { source ->
            drawFrame(state, source, textMeasurer, ::sx, ::sy, zoom, density.density)
        }

        drawBodies(state, fieldModel, textMeasurer, ::sx, ::sy, zoom, density.density, alpha)

        drawProbes(state, fieldModel, ::sx, ::sy, zoom, density.density, alpha)

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
        val isMajor = kotlin.math.abs(startX / (step * 5.0) - (startX / (step * 5.0)).roundToInt()) < 1e-6
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
        val isMajor = kotlin.math.abs(startY / (step * 5.0) - (startY / (step * 5.0)).roundToInt()) < 1e-6
        drawLine(
            color = if (isMajor) GridLineMajor else GridLine,
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
            drawUniformRegion(source.position, source.extent, color, sx, sy, zoom)
            drawElectricFieldIcon(source, color, textMeasurer, sx, sy, zoom)
            if (selected) {
                drawRect(
                    Accent.copy(alpha = 0.9f),
                    Offset(sx(source.position.x - source.extent), sy(source.position.y + source.extent)),
                    Size(
                        (source.extent * 2.0).toFloat() * zoom,
                        (source.extent * 2.0).toFloat() * zoom,
                    ),
                    style = Stroke(2f),
                )
            }
            drawText(
                textMeasurer,
                "E ${"%.1f".format(kotlin.math.abs(source.charge))}",
                topLeft = Offset(center.x + 8f, center.y - 22f),
                style = TextStyle(color = color, fontSize = 9.sp),
            )
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
            drawUniformRegion(source.position, source.extent, FieldMagnetic, sx, sy, zoom)
            drawMagneticFieldSymbols(source, sx, sy, zoom, FieldMagnetic)
            if (selected) {
                drawRect(
                    Accent.copy(alpha = 0.9f),
                    Offset(sx(source.position.x - source.extent), sy(source.position.y + source.extent)),
                    Size(
                        (source.extent * 2.0).toFloat() * zoom,
                        (source.extent * 2.0).toFloat() * zoom,
                    ),
                    style = Stroke(2f),
                )
            }
            drawText(
                textMeasurer,
                "Bz ${"%.1f".format(source.strength)}",
                topLeft = Offset(center.x + 8f, center.y - 22f),
                style = TextStyle(color = FieldMagnetic, fontSize = 9.sp),
            )
        }

        is WallSegment -> Unit
        else -> Unit
    }
}

/** Low-alpha fill and border for a square uniform-field region. */
private fun DrawScope.drawUniformRegion(
    position: Vec2,
    extent: Double,
    color: Color,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val topLeft = Offset(sx(position.x - extent), sy(position.y + extent))
    val size = Size((extent * 2.0).toFloat() * zoom, (extent * 2.0).toFloat() * zoom)
    drawRect(color.copy(alpha = 0.10f), topLeft, size)
    drawRect(color.copy(alpha = 0.55f), topLeft, size, style = Stroke(1.5f))
}

/** Centre arrow showing the uniform E direction (flips with the sign of the strength). */
private fun DrawScope.drawElectricFieldIcon(
    source: PointCharge,
    color: Color,
    textMeasurer: TextMeasurer,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
) {
    val center = Offset(sx(source.position.x), sy(source.position.y))
    val sign = if (source.charge >= 0.0) 1.0 else -1.0
    val dirX = (cos(source.angle) * sign).toFloat()
    val dirY = (-sin(source.angle) * sign).toFloat()
    val length = max(18f, 0.8f * zoom)
    val start = Offset(center.x - dirX * length * 0.5f, center.y - dirY * length * 0.5f)
    val end = Offset(center.x + dirX * length * 0.5f, center.y + dirY * length * 0.5f)
    drawLine(color.copy(alpha = 0.9f), start, end, strokeWidth = 3f)
    drawCircle(color, max(3f, 0.1f * zoom), center)
    val head = length * 0.28f
    val perpX = -dirY
    val perpY = dirX
    drawLine(
        color,
        end,
        Offset(end.x - dirX * head + perpX * head * 0.5f, end.y - dirY * head + perpY * head * 0.5f),
        3f,
    )
    drawLine(
        color,
        end,
        Offset(end.x - dirX * head - perpX * head * 0.5f, end.y - dirY * head - perpY * head * 0.5f),
        3f,
    )
    drawText(
        textMeasurer,
        "E",
        topLeft = Offset(end.x + 4f, end.y - 14f),
        style = TextStyle(color = color, fontSize = 10.sp),
    )
}

/** ⊙ / ⊗ lattice visualising a uniform out-of-plane magnetic field. */
private fun DrawScope.drawMagneticFieldSymbols(
    source: Magnet,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    color: Color,
) {
    val extent = source.extent
    if (extent <= 0.0) return
    val step = 2.0
    val radius = min(10f, max(4f, 0.2f * zoom))
    var gx = -extent + 1.0
    while (gx <= extent - 0.5) {
        var gy = -extent + 1.0
        while (gy <= extent - 0.5) {
            val center = Offset(sx(source.position.x + gx), sy(source.position.y + gy))
            drawCircle(color.copy(alpha = 0.8f), radius, center, style = Stroke(1.4f))
            if (source.strength >= 0.0) {
                drawCircle(color.copy(alpha = 0.8f), radius * 0.28f, center)
            } else {
                val d = radius * 0.55f
                drawLine(
                    color.copy(alpha = 0.8f),
                    Offset(center.x - d, center.y - d),
                    Offset(center.x + d, center.y + d),
                    strokeWidth = 1.4f,
                )
                drawLine(
                    color.copy(alpha = 0.8f),
                    Offset(center.x - d, center.y + d),
                    Offset(center.x + d, center.y - d),
                    strokeWidth = 1.4f,
                )
            }
            gy += step
        }
        gx += step
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
    val cell = min(bounds.width, bounds.height) / 20.0
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
        drawLine(layer.color.copy(alpha = 0.28f + 0.32f * strength.toFloat()), start, end, 1.3f)
        val head = pixelLength * 0.28f
        val perpX = -dirY
        val perpY = dirX
        drawLine(
            layer.color.copy(alpha = 0.55f),
            end,
            Offset(end.x - dirX * head + perpX * head * 0.55f, end.y - dirY * head + perpY * head * 0.55f),
            1.3f,
        )
        drawLine(
            layer.color.copy(alpha = 0.55f),
            end,
            Offset(end.x - dirX * head - perpX * head * 0.55f, end.y - dirY * head - perpY * head * 0.55f),
            1.3f,
        )
    }
}

/** Selection frame + centre/resize handles for the selected field. */
private fun DrawScope.drawFrame(
    state: EditorState,
    source: FieldSource,
    textMeasurer: TextMeasurer,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    density: Float,
) {
    val frame = state.sourceFrame(source)
    val topLeft = Offset(sx(frame.center.x - frame.halfWidth), sy(frame.center.y + frame.halfHeight))
    val size = Size(frame.width.toFloat() * zoom, frame.height.toFloat() * zoom)
    drawRect(
        color = Accent.copy(alpha = 0.9f),
        topLeft = topLeft,
        size = size,
        style = Stroke(
            width = 1.5f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density)),
        ),
    )
    drawCircle(Accent.copy(alpha = 0.15f), 12f * density, Offset(sx(frame.center.x), sy(frame.center.y)))
    drawCircle(Accent, 5f * density, Offset(sx(frame.center.x), sy(frame.center.y)))
    drawCircle(Bg, 2f * density, Offset(sx(frame.center.x), sy(frame.center.y)))

    val corner = Offset(topLeft.x + size.width, topLeft.y)
    val handles = listOf(
        corner,
        Offset(topLeft.x, topLeft.y),
        Offset(topLeft.x, topLeft.y + size.height),
        Offset(topLeft.x + size.width, topLeft.y + size.height),
    )
    for (handle in handles) {
        val handleSize = 12f * density
        drawRect(
            SurfaceRaised,
            handle - Offset(handleSize / 2f, handleSize / 2f),
            Size(handleSize, handleSize),
        )
        drawRect(
            Accent,
            handle - Offset(handleSize / 2f, handleSize / 2f),
            Size(handleSize, handleSize),
            style = Stroke(1.5f * density),
        )
    }

    val label = "%.1f × %.1f m".format(frame.width, frame.height)
    drawText(
        textMeasurer,
        label,
        topLeft = Offset(topLeft.x, max(0f, topLeft.y - 16f * density)),
        style = TextStyle(color = Accent, fontSize = 9.sp),
    )
}

private fun DrawScope.drawBodies(
    state: EditorState,
    fieldModel: FieldModel,
    textMeasurer: TextMeasurer,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    density: Float,
    alpha: Float,
) {
    val bodies = state.bodies
    if (bodies.isEmpty()) return
    val scale = max(8f, 0.5f * zoom)
    val blend = alpha.coerceIn(0f, 1f).toDouble()

    for (body in bodies) {
        val renderPosition = if (body.prevPosition.isFinite() && body.position.isFinite()) {
            body.prevPosition + (body.position - body.prevPosition) * blend
        } else {
            body.position
        }
        val center = Offset(sx(renderPosition.x), sy(renderPosition.y))
        val radius = (body.size * zoom).toFloat().coerceAtLeast(4f)
        val color = when {
            body.charge > 0.05 -> FieldElectric
            body.charge < -0.05 -> Danger
            else -> TextSecondary
        }

        if (state.rules.trajectoryPrediction && bodies.size <= 24) {
            val path = Path()
            val points = predictTrajectory(fieldModel, body)
            if (points.isNotEmpty()) {
                val first = Offset(sx(points[0].x), sy(points[0].y))
                path.moveTo(first.x, first.y)
                for (index in 1 until points.size) {
                    val point = Offset(sx(points[index].x), sy(points[index].y))
                    path.lineTo(point.x, point.y)
                }
                drawPath(
                    path,
                    color.copy(alpha = 0.35f),
                    style = Stroke(1.2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
                )
            }
        }

        if (body.shape == BodyShape.CIRCLE) {
            drawCircle(SurfaceRaised.copy(alpha = 0.55f), radius, center)
            drawCircle(color, radius, center, style = Stroke(2f * density))
        } else {
            rotate(Math.toDegrees(body.angle).toFloat(), center) {
                drawRect(
                    SurfaceRaised.copy(alpha = 0.55f),
                    center - Offset(radius, radius),
                    Size(radius * 2f, radius * 2f),
                )
                drawRect(
                    color,
                    center - Offset(radius, radius),
                    Size(radius * 2f, radius * 2f),
                    style = Stroke(2f * density),
                )
            }
        }

        if (abs(body.charge) > 0.05 && radius > 8f) {
            drawText(
                textMeasurer,
                if (body.charge > 0.0) "+" else "−",
                topLeft = Offset(center.x - 3.5f * density, center.y - 7f * density),
                style = TextStyle(color = color, fontSize = 11.sp),
            )
        }

        if (state.rules.centerOfGravity) {
            drawLine(color.copy(alpha = 0.9f), center - Offset(6f * density, 0f), center + Offset(6f * density, 0f), 1.2f * density)
            drawLine(color.copy(alpha = 0.9f), center - Offset(0f, 6f * density), center + Offset(0f, 6f * density), 1.2f * density)
            drawCircle(Bg, 2.5f * density, center)
        }

        if (state.rules.chargeForceDisplay && body.force.length > 1e-4) {
            drawForceArrow(body.force, center, Accent, density)
        }

        if (state.rules.currentDirection && abs(body.charge) > 0.05 && body.velocity.length > 0.05) {
            drawForceArrow(body.velocity, center + Offset(radius + 10f * density, 0f), FieldElectric, density, alpha = 0.8f)
        }

        if (body.id == state.selectedBodyId) {
            drawCircle(Accent, radius + 5f * density, center, style = Stroke(2f * density))
        }

        if (state.rules.forceTorqueDisplay) {
            drawText(
                textMeasurer,
                "F ${"%.2f".format(body.force.length)} N",
                topLeft = Offset(center.x + radius + 4f * density, center.y - 6f * density),
                style = TextStyle(color = Accent, fontSize = 8.sp),
            )
        }
    }
}

private fun DrawScope.drawForceArrow(
    vector: Vec2,
    origin: Offset,
    color: Color,
    density: Float,
    alpha: Float = 1f,
) {
    val magnitude = vector.length
    if (magnitude <= 1e-9) return
    val length = (16f + min(24f, (log10(1.0 + magnitude) * 14.0).toFloat())) * density
    val dirX = (vector.x / magnitude).toFloat()
    val dirY = -(vector.y / magnitude).toFloat()
    val end = Offset(origin.x + dirX * length, origin.y + dirY * length)
    drawLine(color.copy(alpha = alpha), origin, end, 1.6f * density, cap = StrokeCap.Round)
    val head = 6f * density
    val px = -dirY
    val py = dirX
    drawLine(
        color.copy(alpha = alpha),
        end,
        Offset(end.x - dirX * head + px * head * 0.5f, end.y - dirY * head + py * head * 0.5f),
        1.6f * density,
    )
    drawLine(
        color.copy(alpha = alpha),
        end,
        Offset(end.x - dirX * head - px * head * 0.5f, end.y - dirY * head - py * head * 0.5f),
        1.6f * density,
    )
}

private fun predictTrajectory(field: FieldModel, body: Body, seconds: Double = 1.4): List<Vec2> {
    if (!body.position.isFinite() || !body.velocity.isFinite()) return emptyList()
    val points = ArrayList<Vec2>(36)
    var p = body.position
    var v = body.velocity
    val h = seconds / 30.0
    val invMass = if (body.mass > 0.0) 1.0 / body.mass else 0.0
    val sample = dev.field.core.fields.FieldSample()
    repeat(30) {
        field.sampleInto(p, sample)
        val qOverM = body.charge * invMass
        val ax = qOverM * sample.electric.x + qOverM * sample.magneticBz * -v.y +
            sample.gravity.x + sample.uniform.x
        val ay = qOverM * sample.electric.y + qOverM * sample.magneticBz * v.x +
            sample.gravity.y + sample.uniform.y
        v = Vec2(v.x + ax * h, v.y + ay * h)
        p = Vec2(p.x + v.x * h, p.y + v.y * h)
        if (!p.isFinite()) return points
        points.add(p)
    }
    return points
}

private fun DrawScope.drawProbes(
    state: EditorState,
    fieldModel: FieldModel,
    sx: (Double) -> Float,
    sy: (Double) -> Float,
    zoom: Float,
    density: Float,
    alpha: Float,
) {
    val probes = state.engine.probes
    val blend = alpha.coerceIn(0f, 1f).toDouble()

    fun renderPosition(probe: dev.field.core.sim.Probe): Vec2 =
        if (probe.prevPosition.isFinite() && probe.position.isFinite()) {
            probe.prevPosition + (probe.position - probe.prevPosition) * blend
        } else {
            probe.position
        }

    if (state.visuals.trails && state.rules.trails) {
        val detailed = probes.size <= 200
        for (probe in probes) {
            val trail = probe.trail
            if (trail.size < 2) continue
            val color = if (probe.faulted) Danger else FieldElectric
            if (detailed) {
                // One path per probe instead of ~32 individual draw calls.
                val path = Path()
                val start = max(0, trail.size - 24)
                val first = trail[start]
                path.moveTo(sx(first.x), sy(first.y))
                for (index in start + 1 until trail.size) {
                    val point = trail[index]
                    path.lineTo(sx(point.x), sy(point.y))
                }
                drawPath(path, color.copy(alpha = 0.30f), style = Stroke(1.2f))
            } else {
                val first = trail.first()
                val last = trail.last()
                drawLine(
                    color.copy(alpha = 0.18f),
                    Offset(sx(first.x), sy(first.y)),
                    Offset(sx(last.x), sy(last.y)),
                    strokeWidth = 1f,
                )
            }
        }
    }

    if (state.rules.forceTorqueDisplay && probes.size <= 120) {
        val sample = dev.field.core.fields.FieldSample()
        for (probe in probes) {
            if (probe.faulted) continue
            fieldModel.sampleInto(probe.position, sample)
            val invMass = if (probe.mass > 0.0) 1.0 / probe.mass else 0.0
            val qOverM = probe.charge * invMass
            val force = Vec2(
                qOverM * sample.electric.x + qOverM * sample.magneticBz * -probe.velocity.y +
                    sample.gravity.x + sample.uniform.x,
                qOverM * sample.electric.y + qOverM * sample.magneticBz * probe.velocity.x +
                    sample.gravity.y + sample.uniform.y,
            ) * probe.mass
            if (force.length > 1e-4) {
                val position = renderPosition(probe)
                drawForceArrow(
                    force,
                    Offset(sx(position.x), sy(position.y)),
                    Accent,
                    density,
                    alpha = 0.7f,
                )
            }
        }
    }

    val points = ArrayList<Offset>(probes.size)
    val faulted = ArrayList<Offset>()
    for (probe in probes) {
        val position = renderPosition(probe)
        val offset = Offset(sx(position.x), sy(position.y))
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

    ghost.body?.let { body ->
        val center = Offset(sx(body.position.x), sy(body.position.y))
        val radius = (body.size * zoom).toFloat().coerceAtLeast(5f)
        if (body.shape == BodyShape.CIRCLE) {
            drawCircle(color.copy(alpha = 0.16f), radius, center)
            drawCircle(color, radius, center, style = Stroke(2f))
        } else {
            drawRect(color.copy(alpha = 0.16f), center - Offset(radius, radius), Size(radius * 2f, radius * 2f))
            drawRect(color, center - Offset(radius, radius), Size(radius * 2f, radius * 2f), style = Stroke(2f))
        }
        return
    }

    val source = ghost.source ?: return
    when (source) {
        is PointCharge -> {
            val topLeft = Offset(sx(source.position.x - source.extent), sy(source.position.y + source.extent))
            val extentSize = Size(
                (source.extent * 2.0).toFloat() * zoom,
                (source.extent * 2.0).toFloat() * zoom,
            )
            drawRect(color.copy(alpha = 0.14f), topLeft, extentSize)
            drawRect(color, topLeft, extentSize, style = Stroke(2f))
            drawElectricFieldIcon(source, color, textMeasurer, sx, sy, zoom)
        }

        is Magnet -> {
            val topLeft = Offset(sx(source.position.x - source.extent), sy(source.position.y + source.extent))
            val extentSize = Size(
                (source.extent * 2.0).toFloat() * zoom,
                (source.extent * 2.0).toFloat() * zoom,
            )
            drawRect(color.copy(alpha = 0.14f), topLeft, extentSize)
            drawRect(color, topLeft, extentSize, style = Stroke(2f))
            drawMagneticFieldSymbols(source, sx, sy, zoom, color)
        }

        is MassSource -> {
            val center = Offset(sx(source.position.x), sy(source.position.y))
            val radius = max(12f, 0.45f * zoom)
            drawCircle(color.copy(alpha = 0.16f), radius, center)
            drawCircle(color, radius, center, style = Stroke(2f))
            drawText(
                textMeasurer,
                "M",
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
    val hasGravity = field.sources.any { it is MassSource || it is GravityZone }
    val hasDrag = field.sources.any { it is DragRegion }
    val hasForce = field.sources.any { it is ForceZone }

    val layers = ArrayList<ArrowLayer>(4)
    if (hasElectric) addLayer(layers, field, bounds, FieldKind.ELECTRIC, FieldElectric)
    if (hasGravity) addLayer(layers, field, bounds, FieldKind.GRAVITY, FieldGravity)
    if (hasDrag) addLayer(layers, field, bounds, FieldKind.DRAG, FieldDrag)
    if (hasForce) addLayer(layers, field, bounds, FieldKind.UNIFORM_FORCE, FieldForce)
    return layers
}

private fun addLayer(
    layers: MutableList<ArrowLayer>,
    field: FieldModel,
    bounds: Aabb,
    kind: FieldKind,
    color: Color,
) {
    val samples = FieldViz.arrows(field, bounds, cols = 12, rows = 16, kind = kind)
    val maxMagnitude = samples.maxOfOrNull { it.magnitude } ?: 0.0
    layers.add(ArrowLayer(color, samples, maxMagnitude))
}

private fun buildStreamLayers(field: FieldModel, bounds: Aabb): List<StreamLayer> {
    val layers = ArrayList<StreamLayer>(1)
    if (field.sources.any { it is PointCharge }) {
        val seeds = FieldViz.defaultSeeds(field, FieldKind.ELECTRIC, bounds)
        layers.add(
            StreamLayer(
                FieldElectric,
                FieldViz.streamlines(field, FieldKind.ELECTRIC, seeds, bounds, maxSteps = 200, step = 0.18),
            ),
        )
    }
    return layers
}

private fun buildHeatmap(field: FieldModel, bounds: Aabb, mode: HeatmapMode): ImageBitmap? {
    val cols = 96
    val rows = 96
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
