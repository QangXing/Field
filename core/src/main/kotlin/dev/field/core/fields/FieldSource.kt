package dev.field.core.fields

import dev.field.core.math.Vec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A single field source. Sources accumulate their contribution into [out] in place.
 *
 * [softening] is owned by the enclosing [FieldModel]; it is not part of the persisted
 * representation.
 */
@Serializable
sealed class FieldSource {
    abstract val id: Long

    internal var softening: Double = DEFAULT_SOFTENING

    abstract fun accumulate(p: Vec2, out: FieldSample)

    protected fun softenedDistanceSquared(dx: Double, dy: Double): Double {
        val eps2 = softening * softening
        return dx * dx + dy * dy + eps2
    }

    companion object {
        const val DEFAULT_SOFTENING: Double = 0.05
    }
}

@Serializable
@SerialName("charge")
data class PointCharge(
    override val id: Long,
    val position: Vec2,
    val charge: Double,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        val dx = p.x - position.x
        val dy = p.y - position.y
        val denom = softenedDistanceSquared(dx, dy)
        val invR = 1.0 / sqrt(denom)
        val invR3 = invR / denom
        val scaled = charge * invR3
        out.electric = Vec2(out.electric.x + scaled * dx, out.electric.y + scaled * dy)
        out.potential += charge * invR
    }
}

/**
 * Two-pole magnet approximation. `Bz` is the out-of-plane component produced by a
 * positive (north) and negative (south) pole offset by ±0.35 m along [angle].
 */
@Serializable
@SerialName("magnet")
data class Magnet(
    override val id: Long,
    val position: Vec2,
    val strength: Double,
    val angle: Double,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        val ox = cos(angle) * POLE_OFFSET
        val oy = sin(angle) * POLE_OFFSET
        val northX = position.x + ox
        val northY = position.y + oy
        val southX = position.x - ox
        val southY = position.y - oy

        val northDenom = softenedDistanceSquared(p.x - northX, p.y - northY)
        val southDenom = softenedDistanceSquared(p.x - southX, p.y - southY)
        out.magneticBz += strength * (1.0 / northDenom - 1.0 / southDenom)
    }

    companion object {
        const val POLE_OFFSET: Double = 0.35
    }
}

@Serializable
@SerialName("mass")
data class MassSource(
    override val id: Long,
    val position: Vec2,
    val mass: Double,
) : FieldSource() {
    internal var gravityConstant: Double = 1.0

    override fun accumulate(p: Vec2, out: FieldSample) {
        val dx = p.x - position.x
        val dy = p.y - position.y
        val denom = softenedDistanceSquared(dx, dy)
        val invR = 1.0 / sqrt(denom)
        val invR3 = invR / denom
        val scaled = mass * gravityConstant * invR3
        out.gravity = Vec2(out.gravity.x + scaled * dx, out.gravity.y + scaled * dy)
    }
}

@Serializable
@SerialName("gravityZone")
data class GravityZone(
    override val id: Long,
    val min: Vec2,
    val max: Vec2,
    val gravity: Vec2,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.gravity = out.gravity + gravity
        }
    }

    fun contains(p: Vec2): Boolean =
        p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y
}

@Serializable
@SerialName("forceZone")
data class ForceZone(
    override val id: Long,
    val min: Vec2,
    val max: Vec2,
    val accel: Vec2,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.uniform = out.uniform + accel
        }
    }

    fun contains(p: Vec2): Boolean =
        p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y
}

@Serializable
@SerialName("dragRegion")
data class DragRegion(
    override val id: Long,
    val min: Vec2,
    val max: Vec2,
    val linear: Double = 0.6,
    val quadratic: Double = 0.1,
    val wind: Vec2 = Vec2.ZERO,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.dragLinear += linear
            out.dragQuadratic += quadratic
            out.wind = out.wind + wind
        }
    }

    fun contains(p: Vec2): Boolean =
        p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y
}

@Serializable
@SerialName("wall")
data class WallSegment(
    override val id: Long,
    val start: Vec2,
    val end: Vec2,
    val thickness: Double = 0.2,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        // Static collision geometry only.
    }
}
