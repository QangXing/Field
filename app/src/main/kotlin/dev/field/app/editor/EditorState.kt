package dev.field.app.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.field.app.data.Defaults
import dev.field.core.fields.DragRegion
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
import dev.field.core.scene.CameraDef
import dev.field.core.scene.DisplaySettings
import dev.field.core.scene.Scene
import dev.field.core.scene.VisualDef
import dev.field.core.scene.WorldDef
import dev.field.core.sim.Body
import dev.field.core.sim.BodyDef
import dev.field.core.sim.BodyShape
import dev.field.core.sim.Probe
import dev.field.core.sim.ProbeDef
import dev.field.core.sim.SimSettings
import dev.field.core.sim.SimulationEngine
import dev.field.core.sim.WorldRules
import dev.field.core.viz.HeatmapMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

/** Geometry of a field frame: centre plus half extents, used for drag handles. */
data class SourceFrame(
    val center: Vec2,
    val halfWidth: Double,
    val halfHeight: Double,
) {
    val width: Double get() = halfWidth * 2.0
    val height: Double get() = halfHeight * 2.0
}

/**
 * Single source of truth for one sandbox session: world, sources, moving bodies,
 * probes, camera, placement tool state, rules and undo history.
 */
class EditorState(initial: Scene = Defaults.demoScene()) {

    var sceneName by mutableStateOf(initial.name)
    var bounds by mutableStateOf(initial.world.bounds)
    var boundary by mutableStateOf(initial.world.boundary)
    var globalGravity by mutableStateOf(initial.world.gravity)
    var settings by mutableStateOf(initial.settings)
    var visuals by mutableStateOf(initial.visuals)
    var rules by mutableStateOf(initial.rules)
    var display by mutableStateOf(initial.display)
    var cameraCenter by mutableStateOf(initial.camera.center)
    var zoom by mutableStateOf(initial.camera.zoom)

    var selectedId by mutableStateOf<Long?>(null)
    var selectedBodyId by mutableStateOf<Long?>(null)
    var tool by mutableStateOf<EditorTool?>(null)
    var chargeSign by mutableStateOf(1.0)
    var rotationDeg by mutableStateOf(0)
    var isRunning by mutableStateOf(true)
    var tick by mutableStateOf(0)

    /** Time interpolation factor between the previous and current physics step. */
    var renderAlpha by mutableStateOf(1.0f)
    var dirty by mutableStateOf(false)
    var toastMessage by mutableStateOf<String?>(null)
    var toastToken by mutableStateOf(0)

    /** Defaults used when placing a moving body. */
    var bodyMass by mutableStateOf(1.5)
    var bodyCharge by mutableStateOf(0.0)
    var bodySize by mutableStateOf(0.6)
    var bodySpeed by mutableStateOf(0.0)
    var launchShape by mutableStateOf(BodyShape.CIRCLE)

    val sources = mutableStateListOf<FieldSource>()
    val bodies = mutableStateListOf<Body>()

    var engine: SimulationEngine

    private var nextSourceId = 1L
    private var nextBodyId = 1L
    private val undoStack = ArrayDeque<List<FieldSource>>()
    private val redoStack = ArrayDeque<List<FieldSource>>()
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private val random = Random(1337)

    init {
        sources.addAll(initial.sources)
        nextSourceId = (initial.sources.maxOfOrNull { it.id } ?: 0L) + 1L
        bodies.addAll(
            initial.bodies.map {
                Body(
                    id = it.id,
                    position = it.position,
                    velocity = it.velocity,
                    mass = it.mass,
                    charge = it.charge,
                    shape = it.shape,
                    size = it.size,
                    angle = it.angle,
                )
            },
        )
        nextBodyId = (initial.bodies.maxOfOrNull { it.id } ?: 0L) + 1L
        engine = buildEngine(initial.probes.mapTo(ArrayList()) { def -> probeFrom(def) })
    }

    val selectedSource: FieldSource?
        get() = selectedId?.let { id -> sources.firstOrNull { it.id == id } }

    val selectedBody: Body?
        get() = selectedBodyId?.let { id -> bodies.firstOrNull { it.id == id } }

    val probeCount: Int get() = engine.probes.size

    fun toast(message: String) {
        toastMessage = message
        toastToken++
    }

    private fun markDirty() {
        dirty = true
    }

    private fun probeFrom(def: ProbeDef): Probe = Probe(
        id = def.id,
        position = def.position,
        velocity = def.velocity,
        mass = def.mass,
        charge = def.charge,
        radius = def.radius,
    )

    private fun buildEngine(probeList: MutableList<Probe>): SimulationEngine = SimulationEngine(
        field = FieldModel(sources.toList(), globalGravity, gravityEnabled = rules.gravityEnabled),
        bounds = bounds,
        boundary = boundary,
        settings = settings,
        probes = probeList,
        bodies = bodies,
        rules = rules,
    )

    private fun rebuildEngine() {
        val previous = engine
        engine = buildEngine(previous.probes)
        engine.time = previous.time
        tick++
    }

    // ---------------------------------------------------------------- sources

    fun snap(value: Double): Double {
        val step = settings.gridSnap
        if (step <= 0.0) return value
        return round(value / step) * step
    }

    fun snap(p: Vec2): Vec2 = Vec2(snap(p.x), snap(p.y))

    fun makeSource(
        tool: EditorTool,
        rawPosition: Vec2,
        rotation: Int = rotationDeg,
        sign: Double = chargeSign,
    ): FieldSource? {
        if (tool.isEraser || tool.isBody) return null
        val p = snap(rawPosition)
        val angle = Math.toRadians(rotation.toDouble())
        val id = nextSourceId++
        val half = ZONE_HALF
        return when (tool) {
            EditorTool.ELECTRIC -> PointCharge(
                id = id,
                position = p,
                charge = sign * UNIFORM_STRENGTH,
                extent = UNIFORM_HALF,
                angle = angle,
            )

            EditorTool.MAGNETIC -> Magnet(
                id = id,
                position = p,
                strength = UNIFORM_MAGNETIC,
                angle = 0.0,
                extent = UNIFORM_HALF,
            )
            EditorTool.GRAVITATIONAL -> MassSource(id, p, 8.0)
            EditorTool.GRAVITY -> GravityZone(
                id = id,
                min = Vec2(p.x - half, p.y - half),
                max = Vec2(p.x + half, p.y + half),
                gravity = Vec2(0.0, -3.0).rotate(angle),
            )

            EditorTool.DRAG -> DragRegion(
                id = id,
                min = Vec2(p.x - half, p.y - half),
                max = Vec2(p.x + half, p.y + half),
                linear = 0.6,
                quadratic = 0.1,
            )

            EditorTool.FORCE -> ForceZone(
                id = id,
                min = Vec2(p.x - half, p.y - half),
                max = Vec2(p.x + half, p.y + half),
                accel = Vec2(3.0, 0.0).rotate(angle),
            )

            EditorTool.WALL -> {
                val dir = Vec2(1.0, 0.0).rotate(angle)
                WallSegment(
                    id = id,
                    start = p - dir * (WALL_HALF_LENGTH),
                    end = p + dir * (WALL_HALF_LENGTH),
                    thickness = 0.2,
                )
            }

            EditorTool.BALL, EditorTool.BLOCK, EditorTool.ERASE, EditorTool.SELECT -> null
        }
    }

    fun makeBody(tool: EditorTool, rawPosition: Vec2): Body? {
        if (!tool.isBody) return null
        val p = snap(rawPosition)
        val shape = if (tool == EditorTool.BLOCK) BodyShape.BOX else BodyShape.CIRCLE
        return Body(
            id = nextBodyId++,
            position = p,
            velocity = Vec2(bodySpeed, 0.0).rotate(Math.toRadians(rotationDeg.toDouble())),
            mass = bodyMass,
            charge = bodyCharge,
            shape = shape,
            size = bodySize,
            angle = Math.toRadians(rotationDeg.toDouble()),
        )
    }

    /** Returns null when [source] may be placed, otherwise a user-facing reason. */
    fun validate(source: FieldSource, ignoreId: Long? = null): String? {
        if (sources.size >= MAX_SOURCES) return "已达到场源上限（$MAX_SOURCES）"
        val margin = 0.5

        fun inside(p: Vec2): Boolean = p.x >= bounds.minX + margin &&
            p.x <= bounds.maxX - margin &&
            p.y >= bounds.minY + margin &&
            p.y <= bounds.maxY - margin

        val point = pointPosition(source)
        if (point != null) {
            if (!inside(point)) return "超出世界边界"
            val extent = uniformExtent(source)
            if (extent != null) {
                if (!inside(Vec2(point.x - extent, point.y - extent)) ||
                    !inside(Vec2(point.x + extent, point.y + extent))
                ) {
                    return "边框超出世界边界"
                }
            }
            for (other in sources) {
                if (other.id == ignoreId) continue
                val otherPoint = pointPosition(other) ?: continue
                if ((otherPoint - point).length < MIN_POINT_DISTANCE) {
                    return "与其他场源过近"
                }
            }
        }

        when (source) {
            is GravityZone -> return validateZone(source.min, source.max, ::inside)
            is ForceZone -> return validateZone(source.min, source.max, ::inside)
            is DragRegion -> return validateZone(source.min, source.max, ::inside)
            is WallSegment -> {
                if (!inside(source.start) || !inside(source.end)) return "超出世界边界"
                val length = (source.end - source.start).length
                if (length < 1.0 || length > 20.0) return "墙长必须在 1..20 m"
                val occupied = engine.probes.any { probe ->
                    distanceToSegment(probe.position, source.start, source.end) <
                        probe.radius + source.thickness * 0.5
                }
                if (occupied) return "出生点被墙占据"
            }

            else -> Unit
        }
        return null
    }

    fun validateBody(body: Body): String? {
        if (bodies.size >= MAX_BODIES) return "已达到物体上限（$MAX_BODIES）"
        val margin = 0.2
        val p = body.position
        if (p.x < bounds.minX + margin || p.x > bounds.maxX - margin ||
            p.y < bounds.minY + margin || p.y > bounds.maxY - margin
        ) {
            return "超出世界边界"
        }
        if (body.mass <= 0.0) return "质量必须为正"
        if (body.size < 0.1 || body.size > 5.0) return "尺寸必须在 0.1..5 m"
        return null
    }

    private fun validateZone(min: Vec2, max: Vec2, inside: (Vec2) -> Boolean): String? {
        if (!inside(min) || !inside(max)) return "超出世界边界"
        val w = max.x - min.x
        val h = max.y - min.y
        if (w < 1.0 || h < 1.0 || w > 20.0 || h > 20.0) return "尺寸必须在 1..20 m"
        return null
    }

    fun placeAt(rawPosition: Vec2): Boolean {
        val active = tool ?: return false
        if (active.isEraser) return false
        if (active.isBody) {
            val candidate = makeBody(active, rawPosition) ?: return false
            val reason = validateBody(candidate)
            if (reason != null) {
                toast(reason)
                return false
            }
            bodies.add(candidate)
            selectedBodyId = candidate.id
            selectedId = null
            markDirty()
            return true
        }
        val candidate = makeSource(active, rawPosition) ?: return false
        val reason = validate(candidate)
        if (reason != null) {
            toast(reason)
            return false
        }
        pushUndo()
        sources.add(candidate)
        selectedId = candidate.id
        selectedBodyId = null
        rebuildEngine()
        markDirty()
        return true
    }

    fun placementSpacing(tool: EditorTool): Double = when {
        tool.pointLike || tool.isBody -> 1.0
        tool == EditorTool.ELECTRIC || tool == EditorTool.MAGNETIC -> UNIFORM_HALF * 2.0 + 0.5
        tool == EditorTool.WALL -> WALL_HALF_LENGTH * 2 + 0.5
        else -> ZONE_HALF * 2 + 0.5
    }

    fun linePositions(from: Vec2, to: Vec2): List<Vec2> {
        val active = tool ?: return listOf(snap(from))
        if (active.isEraser) return emptyList()
        val spacing = placementSpacing(active)
        val start = snap(from)
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (hypot(dx, dy) < spacing * 0.35) return listOf(start)

        val horizontal = abs(dx) >= abs(dy)
        val direction = if (horizontal) {
            if (dx >= 0.0) 1.0 else -1.0
        } else {
            if (dy >= 0.0) 1.0 else -1.0
        }
        val distance = if (horizontal) abs(dx) else abs(dy)
        val count = (floor(distance / spacing) + 1).toInt().coerceIn(1, MAX_LINE_PLACEMENTS)
        return (0 until count).map { index ->
            val offset = direction * spacing * index
            if (horizontal) Vec2(start.x + offset, start.y) else Vec2(start.x, start.y + offset)
        }
    }

    fun placeLine(from: Vec2, to: Vec2): Int {
        val active = tool ?: return 0
        if (active.isEraser) return 0
        val positions = linePositions(from, to)
        if (active.isBody) {
            val candidates = positions.mapNotNull { makeBody(active, it) }
            val valid = candidates.filter { validateBody(it) == null }
            if (valid.isEmpty()) {
                toast(candidates.firstOrNull()?.let { validateBody(it) } ?: "无法放置")
                return 0
            }
            bodies.addAll(valid)
            selectedBodyId = valid.last().id
            selectedId = null
            markDirty()
            if (valid.size > 1) toast("已放置 ${valid.size} 个物体")
            return valid.size
        }
        val candidates = positions.mapNotNull { makeSource(active, it) }
        if (candidates.isEmpty()) return 0
        val valid = candidates.filter { validate(it) == null }
        if (valid.isEmpty()) {
            toast(validate(candidates.first()) ?: "无法放置")
            return 0
        }
        pushUndo()
        sources.addAll(valid)
        selectedId = valid.last().id
        selectedBodyId = null
        rebuildEngine()
        markDirty()
        if (valid.size > 1) toast("已放置 ${valid.size} 个场单元")
        return valid.size
    }

    fun eraseAt(point: Vec2, recordUndo: Boolean = true): Int {
        val body = hitTestBody(point)
        if (body != null) {
            bodies.removeAll { it.id == body.id }
            if (selectedBodyId == body.id) selectedBodyId = null
            markDirty()
            return 1
        }
        val hit = hitTest(point) ?: return 0
        if (recordUndo) pushUndo()
        sources.removeAll { it.id == hit.id }
        if (selectedId == hit.id) selectedId = null
        rebuildEngine()
        markDirty()
        return 1
    }

    fun removeSource(id: Long) {
        if (sources.none { it.id == id }) return
        pushUndo()
        sources.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
        rebuildEngine()
        markDirty()
    }

    fun removeBody(id: Long) {
        bodies.removeAll { it.id == id }
        if (selectedBodyId == id) selectedBodyId = null
        tick++
        markDirty()
    }

    fun select(id: Long?) {
        selectedId = id
        if (id != null) selectedBodyId = null
    }

    fun selectBody(id: Long?) {
        selectedBodyId = id
        if (id != null) selectedId = null
    }

    fun hitTestBody(point: Vec2): Body? {
        val padding = max(0.15, 14.0 / zoom)
        return bodies.lastOrNull { body ->
            (body.position - point).length <= body.collisionRadius + padding
        }
    }

    fun hitTest(point: Vec2): FieldSource? {
        val pointPadding = max(0.35, 18.0 / zoom)
        return sources.lastOrNull { source ->
            when (source) {
                is PointCharge -> insideUniform(point, source.position, source.extent)
                is MassSource -> (source.position - point).length <= pointPadding
                is Magnet -> insideUniform(point, source.position, source.extent)
                is GravityZone -> source.contains(point)
                is ForceZone -> source.contains(point)
                is DragRegion -> source.contains(point)
                is WallSegment ->
                    distanceToSegment(point, source.start, source.end) <=
                    source.thickness * 0.5 + max(0.2, 12.0 / zoom)
            }
        }
    }

    fun translate(source: FieldSource, delta: Vec2): FieldSource = when (source) {
        is PointCharge -> source.copy(position = snap(source.position + delta))
        is Magnet -> source.copy(position = snap(source.position + delta))
        is MassSource -> source.copy(position = snap(source.position + delta))
        is GravityZone -> {
            val d = Vec2(snap(delta.x), snap(delta.y))
            source.copy(min = source.min + d, max = source.max + d)
        }

        is ForceZone -> {
            val d = Vec2(snap(delta.x), snap(delta.y))
            source.copy(min = source.min + d, max = source.max + d)
        }

        is DragRegion -> {
            val d = Vec2(snap(delta.x), snap(delta.y))
            source.copy(min = source.min + d, max = source.max + d)
        }

        is WallSegment -> {
            val d = Vec2(snap(delta.x), snap(delta.y))
            source.copy(start = source.start + d, end = source.end + d)
        }
    }

    fun moveSourceBy(id: Long, delta: Vec2) {
        val index = sources.indexOfFirst { it.id == id }
        if (index < 0) return
        val moved = translate(sources[index], delta)
        sources[index] = moved
        rebuildEngine()
        markDirty()
    }

    fun moveBodyBy(id: Long, delta: Vec2) {
        val body = bodies.firstOrNull { it.id == id } ?: return
        body.position = body.position + delta
        body.prevPosition = body.position
        renderAlpha = 1f
        tick++
        markDirty()
    }

    fun updateSource(updated: FieldSource) {
        val index = sources.indexOfFirst { it.id == updated.id }
        if (index < 0) return
        sources[index] = updated
        rebuildEngine()
        markDirty()
    }

    fun updateSelected(transform: (FieldSource) -> FieldSource) {
        val current = selectedSource ?: return
        updateSource(transform(current))
    }

    fun updateSelectedBody(transform: (Body) -> Unit) {
        selectedBody?.let {
            transform(it)
            markDirty()
            tick++
        }
    }

    // ---------------------------------------------------------------- frames

    fun sourceFrame(source: FieldSource): SourceFrame = when (source) {
        is PointCharge -> SourceFrame(source.position, source.extent, source.extent)
        is Magnet -> SourceFrame(source.position, source.extent, source.extent)
        is MassSource -> SourceFrame(source.position, source.extent, source.extent)
        is GravityZone -> SourceFrame((source.min + source.max) * 0.5, (source.max.x - source.min.x) * 0.5, (source.max.y - source.min.y) * 0.5)
        is ForceZone -> SourceFrame((source.min + source.max) * 0.5, (source.max.x - source.min.x) * 0.5, (source.max.y - source.min.y) * 0.5)
        is DragRegion -> SourceFrame((source.min + source.max) * 0.5, (source.max.x - source.min.x) * 0.5, (source.max.y - source.min.y) * 0.5)
        is WallSegment -> {
            val length = (source.end - source.start).length
            SourceFrame((source.start + source.end) * 0.5, length * 0.5, max(source.thickness * 0.5, 0.5))
        }
    }

    fun moveSourceTo(id: Long, center: Vec2) {
        val source = sources.firstOrNull { it.id == id } ?: return
        val frame = sourceFrame(source)
        val delta = center - frame.center
        val moved = translate(source, delta)
        updateSource(moved)
    }

    fun resizeSource(id: Long, halfWidth: Double, halfHeight: Double) {
        val source = sources.firstOrNull { it.id == id } ?: return
        val frame = sourceFrame(source)
        val hw = halfWidth.coerceIn(0.5, 10.0)
        val hh = halfHeight.coerceIn(0.5, 10.0)
        val updated = when (source) {
            is PointCharge -> source.copy(extent = max(hw, hh).coerceIn(0.5, 20.0))
            is Magnet -> source.copy(extent = max(hw, hh).coerceIn(0.5, 20.0))
            is MassSource -> source.copy(extent = max(hw, hh).coerceIn(0.5, 20.0))
            is GravityZone -> source.copy(
                min = Vec2(frame.center.x - hw, frame.center.y - hh),
                max = Vec2(frame.center.x + hw, frame.center.y + hh),
            )

            is ForceZone -> source.copy(
                min = Vec2(frame.center.x - hw, frame.center.y - hh),
                max = Vec2(frame.center.x + hw, frame.center.y + hh),
            )

            is DragRegion -> source.copy(
                min = Vec2(frame.center.x - hw, frame.center.y - hh),
                max = Vec2(frame.center.x + hw, frame.center.y + hh),
            )

            is WallSegment -> {
                val dir = (source.end - source.start).normalized().let {
                    if (it.lengthSquared == 0.0) Vec2(1.0, 0.0) else it
                }
                val half = max(hw, 1.0).coerceIn(0.5, 10.0)
                source.copy(
                    start = frame.center - dir * half,
                    end = frame.center + dir * half,
                )
            }
        }
        updateSource(updated)
    }

    // ---------------------------------------------------------------- undo

    fun pushUndo() {
        undoStack.addLast(sources.toList())
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(sources.toList())
        applySources(previous)
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(sources.toList())
        applySources(next)
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun applySources(list: List<FieldSource>) {
        sources.clear()
        sources.addAll(list)
        selectedId = null
        rebuildEngine()
        markDirty()
    }

    // ---------------------------------------------------------------- probes & bodies

    fun launchProbes(
        count: Int,
        speed: Double,
        charge: Double,
        spreadDegrees: Double,
        direction: Vec2 = Vec2(1.0, 0.0),
    ) {
        val origin = bounds.center
        val allowed = count.coerceIn(0, MAX_PROBES - engine.probes.size)
        if (allowed <= 0) {
            toast("探针数量已达上限（$MAX_PROBES）")
            return
        }
        engine.launch(
            count = allowed,
            origin = origin,
            direction = direction,
            speed = speed,
            charge = charge,
            spreadRadians = Math.toRadians(spreadDegrees),
            rng = random,
        )
        tick++
        markDirty()
    }

    fun launchBodies(
        count: Int,
        shape: BodyShape,
        mass: Double,
        charge: Double,
        speed: Double,
        size: Double,
        spreadDegrees: Double,
    ) {
        val allowed = count.coerceIn(0, MAX_BODIES - bodies.size)
        if (allowed <= 0) {
            toast("物体数量已达上限（$MAX_BODIES）")
            return
        }
        val origin = bounds.center
        val baseAngle = 0.0
        val halfSpread = Math.toRadians(spreadDegrees) * 0.5
        repeat(allowed) {
            val angle = if (halfSpread > 0.0) baseAngle + random.nextDouble(-halfSpread, halfSpread) else baseAngle
            engine.addBody(
                Body(
                    id = nextBodyId++,
                    position = origin,
                    velocity = Vec2(kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed),
                    mass = mass,
                    charge = charge,
                    shape = shape,
                    size = size,
                ),
            )
        }
        tick++
        markDirty()
    }

    fun clearProbes() {
        engine.clearProbes()
        tick++
        markDirty()
    }

    fun clearBodies() {
        bodies.clear()
        if (selectedBodyId != null) selectedBodyId = null
        tick++
        markDirty()
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(transform: (SimSettings) -> SimSettings) {
        settings = transform(settings)
        engine.settings = settings
        markDirty()
    }

    fun updateVisuals(transform: (VisualDef) -> VisualDef) {
        visuals = transform(visuals)
        markDirty()
    }

    fun updateRules(transform: (WorldRules) -> WorldRules) {
        val previous = rules
        rules = transform(rules)
        engine.rules = rules
        if (previous.gravityEnabled != rules.gravityEnabled) {
            rebuildEngine()
        }
        markDirty()
    }

    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) {
        display = transform(display)
        markDirty()
    }

    fun updateWorld(transform: (WorldDef) -> WorldDef) {
        val world = transform(WorldDef(bounds, boundary, globalGravity))
        bounds = world.bounds
        boundary = world.boundary
        globalGravity = world.gravity
        rebuildEngine()
        markDirty()
    }

    fun speedUp() {
        updateSettings { it.copy(speed = nextSpeed(it.speed, forward = true)) }
    }

    fun speedDown() {
        updateSettings { it.copy(speed = nextSpeed(it.speed, forward = false)) }
    }

    fun rotate() {
        rotationDeg = (rotationDeg + 45) % 360
    }

    fun resetRotation() {
        rotationDeg = 0
    }

    fun heatmapMode(): HeatmapMode = visuals.heatmapMode

    // ---------------------------------------------------------------- scene

    fun toScene(): Scene = Scene(
        name = sceneName,
        world = WorldDef(bounds = bounds, boundary = boundary, gravity = globalGravity),
        settings = settings,
        sources = sources.toList(),
        probes = engine.probes.map {
            ProbeDef(
                id = it.id,
                position = it.position,
                velocity = it.velocity,
                mass = it.mass,
                charge = it.charge,
                radius = it.radius,
            )
        },
        camera = CameraDef(center = cameraCenter, zoom = zoom),
        visuals = visuals,
        bodies = bodies.map { it.toDef() },
        rules = rules,
        display = display,
    )

    fun loadScene(scene: Scene) {
        sceneName = scene.name
        bounds = scene.world.bounds
        boundary = scene.world.boundary
        globalGravity = scene.world.gravity
        settings = scene.settings
        visuals = scene.visuals
        rules = scene.rules
        display = scene.display
        cameraCenter = scene.camera.center
        zoom = scene.camera.zoom
        sources.clear()
        sources.addAll(scene.sources)
        nextSourceId = (scene.sources.maxOfOrNull { it.id } ?: 0L) + 1L
        bodies.clear()
        bodies.addAll(
            scene.bodies.map {
                Body(
                    id = it.id,
                    position = it.position,
                    velocity = it.velocity,
                    mass = it.mass,
                    charge = it.charge,
                    shape = it.shape,
                    size = it.size,
                    angle = it.angle,
                )
            },
        )
        nextBodyId = (scene.bodies.maxOfOrNull { it.id } ?: 0L) + 1L
        engine = buildEngine(scene.probes.mapTo(ArrayList()) { def -> probeFrom(def) })
        selectedId = null
        selectedBodyId = null
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        dirty = false
        tick++
    }

    companion object {
        const val MAX_SOURCES = 200
        const val MAX_PROBES = 2000
        const val MAX_BODIES = 300
        const val UNDO_LIMIT = 20
        const val MIN_POINT_DISTANCE = 0.5
        const val ZONE_HALF = 2.0
        const val WALL_HALF_LENGTH = 2.0
        const val MAX_LINE_PLACEMENTS = 40
        const val UNIFORM_HALF = 3.0
        const val UNIFORM_STRENGTH = 3.0
        const val UNIFORM_MAGNETIC = 1.5

        private val SPEEDS = listOf(0.25, 0.5, 1.0, 2.0, 4.0)

        private fun nextSpeed(current: Double, forward: Boolean): Double {
            val index = SPEEDS.indexOfFirst { abs(it - current) < 1e-6 }
                .let { if (it < 0) 2 else it }
            val next = if (forward) min(index + 1, SPEEDS.lastIndex)
            else max(index - 1, 0)
            return SPEEDS[next]
        }

        fun pointPosition(source: FieldSource): Vec2? = when (source) {
            is PointCharge -> source.position
            is MassSource -> source.position
            is Magnet -> source.position
            else -> null
        }

        fun uniformExtent(source: FieldSource): Double? = when (source) {
            is PointCharge -> source.extent
            is Magnet -> source.extent
            else -> null
        }

        fun insideUniform(point: Vec2, center: Vec2, extent: Double): Boolean =
            abs(point.x - center.x) <= extent && abs(point.y - center.y) <= extent

        fun electricAngleDegrees(source: PointCharge): Int =
            Math.toDegrees(source.angle).let { ((it % 360 + 360) % 360).toInt() }

        fun distanceToSegment(p: Vec2, start: Vec2, end: Vec2): Double {
            val segment = end - start
            val lengthSquared = segment.lengthSquared
            if (lengthSquared < 1e-12) return (p - start).length
            val t = (((p.x - start.x) * segment.x + (p.y - start.y) * segment.y) /
                lengthSquared).coerceIn(0.0, 1.0)
            val closest = start + segment * t
            return (p - closest).length
        }

        fun zoneSize(source: FieldSource): Vec2? = when (source) {
            is GravityZone -> source.max - source.min
            is ForceZone -> source.max - source.min
            is DragRegion -> source.max - source.min
            else -> null
        }

        fun zoneCenter(source: FieldSource): Vec2? = when (source) {
            is GravityZone -> (source.min + source.max) * 0.5
            is ForceZone -> (source.min + source.max) * 0.5
            is DragRegion -> (source.min + source.max) * 0.5
            else -> null
        }

        fun wallAngleDegrees(source: WallSegment): Int =
            Math.toDegrees(atan2(source.end.y - source.start.y, source.end.x - source.start.x))
                .let { ((it % 360 + 360) % 360).toInt() }

        fun magnetAngleDegrees(source: Magnet): Int =
            Math.toDegrees(source.angle).let { ((it % 360 + 360) % 360).toInt() }

        fun magneticSign(source: Magnet): String =
            if (source.strength >= 0.0) "⊙ 出屏" else "⊗ 入屏"

        fun gravityAngleDegrees(source: GravityZone): Int =
            Math.toDegrees(atan2(source.gravity.y, source.gravity.x))
                .let { ((it % 360 + 360) % 360).toInt() }

        fun forceAngleDegrees(source: ForceZone): Int =
            Math.toDegrees(atan2(source.accel.y, source.accel.x))
                .let { ((it % 360 + 360) % 360).toInt() }

        fun zoneSide(source: FieldSource): Double? {
            val size = zoneSize(source) ?: return null
            return sqrt(size.lengthSquared * 0.5)
        }

        fun format(value: Double, digits: Int = 1): String {
            if (!value.isFinite()) return "—"
            val factor = Math.pow(10.0, digits.toDouble())
            val rounded = round(value * factor) / factor
            return if (digits == 0) rounded.toLong().toString() else rounded.toString()
        }
    }
}
