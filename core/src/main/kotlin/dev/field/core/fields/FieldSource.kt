package dev.field.core.fields

import dev.field.core.math.Vec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Frame shape of a field source. */
@Serializable
enum class FieldShape {
    @SerialName("rect")
    RECT,

    @SerialName("circle")
    CIRCLE,
}

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

    /** True when [p] lies inside the frame centred at [center] with the given half extents. */
    protected fun insideFrame(
        p: Vec2,
        center: Vec2,
        halfWidth: Double,
        halfHeight: Double,
        shape: FieldShape,
    ): Boolean {
        val dx = p.x - center.x
        val dy = p.y - center.y
        return when (shape) {
            FieldShape.RECT -> abs(dx) <= halfWidth && abs(dy) <= halfHeight
            FieldShape.CIRCLE -> {
                val radius = min(halfWidth, halfHeight)
                dx * dx + dy * dy <= radius * radius
            }
        }
    }

    companion object {
        const val DEFAULT_SOFTENING: Double = 0.05
        const val DEFAULT_EXTENT: Double = 8.0
    }
}

/**
 * Uniform electric field over a square or circular region.
 *
 * The legacy name is kept so existing JSON v2/v3 saves remain readable.
 * Inside the frame [out] receives `|charge|` in the direction [angle]; outside it is zero.
 * Negative [charge] reverses the direction.
 */
@Serializable
@SerialName("charge")
data class PointCharge(
    override val id: Long,
    val position: Vec2,
    /** Signed field strength along [angle]. */
    val charge: Double,
    /** Half side (rect) or radius (circle) of the frame in metres. */
    val extent: Double = DEFAULT_EXTENT,
    /** Direction of the field in radians. */
    val angle: Double = 0.0,
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (!insideFrame(p, position, extent, extent, shape)) return
        val ex = cos(angle) * charge
        val ey = sin(angle) * charge
        out.electric = Vec2(out.electric.x + ex, out.electric.y + ey)
        // Potential of a uniform field: -E·(p - centre).
        out.potential += -(ex * (p.x - position.x) + ey * (p.y - position.y))
    }
}

/**
 * Uniform out-of-plane magnetic field `Bz` over a square or circular region.
 *
 * The legacy name (and the stored [angle] field) are kept for JSON compatibility;
 * a uniform perpendicular field has no in-plane orientation, so [angle] is ignored.
 */
@Serializable
@SerialName("magnet")
data class Magnet(
    override val id: Long,
    val position: Vec2,
    /** Signed out-of-plane magnetic flux density. */
    val strength: Double,
    val angle: Double = 0.0,
    /** Half side (rect) or radius (circle) of the frame in metres. */
    val extent: Double = DEFAULT_EXTENT,
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (!insideFrame(p, position, extent, extent, shape)) return
        out.magneticBz += strength
    }
}

@Serializable
@SerialName("mass")
data class MassSource(
    override val id: Long,
    val position: Vec2,
    val mass: Double,
    val extent: Double = DEFAULT_EXTENT,
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    internal var gravityConstant: Double = 1.0

    override fun accumulate(p: Vec2, out: FieldSample) {
        if (!insideFrame(p, position, extent, extent, shape)) return
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
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.gravity = out.gravity + gravity
        }
    }

    fun contains(p: Vec2): Boolean = insideFrame(
        p,
        Vec2((min.x + max.x) * 0.5, (min.y + max.y) * 0.5),
        (max.x - min.x) * 0.5,
        (max.y - min.y) * 0.5,
        shape,
    )
}

@Serializable
@SerialName("forceZone")
data class ForceZone(
    override val id: Long,
    val min: Vec2,
    val max: Vec2,
    val accel: Vec2,
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.uniform = out.uniform + accel
        }
    }

    fun contains(p: Vec2): Boolean = insideFrame(
        p,
        Vec2((min.x + max.x) * 0.5, (min.y + max.y) * 0.5),
        (max.x - min.x) * 0.5,
        (max.y - min.y) * 0.5,
        shape,
    )
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
    val shape: FieldShape = FieldShape.RECT,
) : FieldSource() {
    override fun accumulate(p: Vec2, out: FieldSample) {
        if (contains(p)) {
            out.dragLinear += linear
            out.dragQuadratic += quadratic
            out.wind = out.wind + wind
        }
    }

    fun contains(p: Vec2): Boolean = insideFrame(
        p,
        Vec2((min.x + max.x) * 0.5, (min.y + max.y) * 0.5),
        (max.x - min.x) * 0.5,
        (max.y - min.y) * 0.5,
        shape,
    )
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
