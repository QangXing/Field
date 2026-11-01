package dev.field.core.scene

import dev.field.core.fields.DragRegion
import dev.field.core.fields.FieldSource
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.sim.BodyDef
import dev.field.core.sim.ProbeDef

object SceneValidator {
    private const val MAX_SOURCES = 200
    private const val MAX_PROBES = 2000
    private const val MAX_BODIES = 300
    private const val MIN_WORLD_SIDE = 1.0
    private const val MAX_WORLD_SIDE = 200.0
    private const val MIN_ZONE_SIDE = 1.0
    private const val MAX_ZONE_SIDE = 20.0
    private const val MIN_EXTENT = 0.5
    private const val MAX_EXTENT = 20.0

    fun validate(scene: Scene): List<SceneError> {
        val errors = ArrayList<SceneError>()

        if (scene.version !in 2..3) {
            errors.add(SceneError("version", "必须为 2 或 3"))
        }

        validateBounds(scene.world.bounds, errors)
        validateVec(errors, "world.gravity", scene.world.gravity)
        validateSettings(scene, errors)

        if (scene.sources.size > MAX_SOURCES) {
            errors.add(SceneError("sources", "不得超过 $MAX_SOURCES 个"))
        }
        val seenIds = HashSet<Long>()
        for ((index, source) in scene.sources.withIndex()) {
            if (!seenIds.add(source.id)) {
                errors.add(SceneError("sources[$index].id", "必须唯一"))
            }
            validateSource(index, source, errors)
        }

        if (scene.probes.size > MAX_PROBES) {
            errors.add(SceneError("probes", "不得超过 $MAX_PROBES 个"))
        }
        for ((index, probe) in scene.probes.withIndex()) {
            validateProbe(index, probe, errors)
        }

        if (scene.bodies.size > MAX_BODIES) {
            errors.add(SceneError("bodies", "不得超过 $MAX_BODIES 个"))
        }
        val bodyIds = HashSet<Long>()
        for ((index, body) in scene.bodies.withIndex()) {
            if (!bodyIds.add(body.id)) {
                errors.add(SceneError("bodies[$index].id", "必须唯一"))
            }
            validateBody(index, body, errors)
        }

        validateVec(errors, "camera.center", scene.camera.center)
        validateNumber(errors, "camera.zoom", scene.camera.zoom)
        if (scene.camera.zoom <= 0.0) {
            errors.add(SceneError("camera.zoom", "必须为正数"))
        }

        return errors
    }

    private fun validateBounds(bounds: Aabb, errors: MutableList<SceneError>) {
        validateNumber(errors, "bounds.minX", bounds.minX)
        validateNumber(errors, "bounds.minY", bounds.minY)
        validateNumber(errors, "bounds.maxX", bounds.maxX)
        validateNumber(errors, "bounds.maxY", bounds.maxY)

        if (!(bounds.minX < bounds.maxX)) {
            errors.add(SceneError("bounds.maxX", "必须大于 minX"))
        } else {
            val width = bounds.width
            if (width < MIN_WORLD_SIDE || width > MAX_WORLD_SIDE) {
                errors.add(SceneError("bounds", "宽度必须在 1..200 m"))
            }
        }
        if (!(bounds.minY < bounds.maxY)) {
            errors.add(SceneError("bounds.maxY", "必须大于 minY"))
        } else {
            val height = bounds.height
            if (height < MIN_WORLD_SIDE || height > MAX_WORLD_SIDE) {
                errors.add(SceneError("bounds", "高度必须在 1..200 m"))
            }
        }
    }

    private fun validateSettings(scene: Scene, errors: MutableList<SceneError>) {
        validateNumber(errors, "settings.fixedDt", scene.settings.fixedDt)
        validateNumber(errors, "settings.speed", scene.settings.speed)
        validateNumber(errors, "settings.gridSnap", scene.settings.gridSnap)
    }

    private fun validateExtent(prefix: String, extent: Double, errors: MutableList<SceneError>) {
        validateNumber(errors, "$prefix.extent", extent)
        if (extent < MIN_EXTENT || extent > MAX_EXTENT) {
            errors.add(SceneError("$prefix.extent", "边框半径必须在 0.5..20 m"))
        }
    }

    private fun validateSource(
        index: Int,
        source: FieldSource,
        errors: MutableList<SceneError>,
    ) {
        val prefix = "sources[$index]"
        when (source) {
            is PointCharge -> {
                validateVec(errors, "$prefix.position", source.position)
                validateNumber(errors, "$prefix.charge", source.charge)
                validateNumber(errors, "$prefix.angle", source.angle)
                validateExtent(prefix, source.extent, errors)
                if (source.charge == 0.0) {
                    errors.add(SceneError("$prefix.charge", "不能为 0"))
                }
            }

            is Magnet -> {
                validateVec(errors, "$prefix.position", source.position)
                validateNumber(errors, "$prefix.strength", source.strength)
                validateNumber(errors, "$prefix.angle", source.angle)
                validateExtent(prefix, source.extent, errors)
                if (source.strength == 0.0) {
                    errors.add(SceneError("$prefix.strength", "不能为 0"))
                }
            }

            is MassSource -> {
                validateVec(errors, "$prefix.position", source.position)
                validateNumber(errors, "$prefix.mass", source.mass)
                validateExtent(prefix, source.extent, errors)
                if (source.mass == 0.0) {
                    errors.add(SceneError("$prefix.mass", "不能为 0"))
                }
            }

            is GravityZone -> {
                validateZone(prefix, source.min, source.max, errors)
                validateVec(errors, "$prefix.gravity", source.gravity)
            }

            is ForceZone -> {
                validateZone(prefix, source.min, source.max, errors)
                validateVec(errors, "$prefix.accel", source.accel)
            }

            is DragRegion -> {
                validateZone(prefix, source.min, source.max, errors)
                validateNumber(errors, "$prefix.linear", source.linear)
                validateNumber(errors, "$prefix.quadratic", source.quadratic)
                validateVec(errors, "$prefix.wind", source.wind)
            }

            is WallSegment -> {
                validateVec(errors, "$prefix.start", source.start)
                validateVec(errors, "$prefix.end", source.end)
                validateNumber(errors, "$prefix.thickness", source.thickness)
            }
        }
    }

    private fun validateZone(
        prefix: String,
        min: Vec2,
        max: Vec2,
        errors: MutableList<SceneError>,
    ) {
        validateVec(errors, "$prefix.min", min)
        validateVec(errors, "$prefix.max", max)
        if (!(min.x < max.x) || !(min.y < max.y)) {
            errors.add(SceneError("$prefix.max", "必须大于 min"))
            return
        }
        val width = max.x - min.x
        val height = max.y - min.y
        if (width < MIN_ZONE_SIDE || width > MAX_ZONE_SIDE ||
            height < MIN_ZONE_SIDE || height > MAX_ZONE_SIDE
        ) {
            errors.add(SceneError(prefix, "区域边长必须在 1..20 m"))
        }
    }

    private fun validateProbe(index: Int, probe: ProbeDef, errors: MutableList<SceneError>) {
        val prefix = "probes[$index]"
        validateVec(errors, "$prefix.position", probe.position)
        validateVec(errors, "$prefix.velocity", probe.velocity)
        validateNumber(errors, "$prefix.mass", probe.mass)
        validateNumber(errors, "$prefix.charge", probe.charge)
        validateNumber(errors, "$prefix.radius", probe.radius)
        if (probe.mass == 0.0) {
            errors.add(SceneError("$prefix.mass", "不能为 0"))
        }
    }

    private fun validateBody(index: Int, body: BodyDef, errors: MutableList<SceneError>) {
        val prefix = "bodies[$index]"
        validateVec(errors, "$prefix.position", body.position)
        validateVec(errors, "$prefix.velocity", body.velocity)
        validateNumber(errors, "$prefix.mass", body.mass)
        validateNumber(errors, "$prefix.charge", body.charge)
        validateNumber(errors, "$prefix.size", body.size)
        validateNumber(errors, "$prefix.angle", body.angle)
        if (body.mass <= 0.0) {
            errors.add(SceneError("$prefix.mass", "必须为正数"))
        }
        if (body.size < 0.1 || body.size > 5.0) {
            errors.add(SceneError("$prefix.size", "尺寸必须在 0.1..5 m"))
        }
    }

    private fun validateVec(errors: MutableList<SceneError>, path: String, value: Vec2) {
        if (!value.isFinite()) {
            errors.add(SceneError(path, "必须为有限数"))
        }
    }

    private fun validateNumber(errors: MutableList<SceneError>, path: String, value: Double) {
        if (!value.isFinite()) {
            errors.add(SceneError(path, "必须为有限数"))
        }
    }
}
