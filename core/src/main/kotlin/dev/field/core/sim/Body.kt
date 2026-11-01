package dev.field.core.sim

import dev.field.core.math.Vec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** Shape of a moving body. */
@Serializable
enum class BodyShape {
    @SerialName("circle")
    CIRCLE,

    @SerialName("box")
    BOX,
}

/** Persisted description of a moving body (ball or block). */
@Serializable
data class BodyDef(
    val id: Long = 0,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val mass: Double = 1.0,
    val charge: Double = 0.0,
    val shape: BodyShape = BodyShape.CIRCLE,
    /** Radius for circles, half side length for boxes. */
    val size: Double = 0.5,
    val angle: Double = 0.0,
)

/**
 * A dynamic rigid body approximated by a circle for collision purposes.
 *
 * Bodies move under the same field forces as probes, additionally feel Coulomb
 * forces from other bodies when the world rule is enabled, and expose their
 * last net force for the force/centre-of-gravity overlays.
 */
class Body(
    val id: Long,
    position: Vec2,
    velocity: Vec2,
    mass: Double = 1.0,
    charge: Double = 0.0,
    val shape: BodyShape = BodyShape.CIRCLE,
    size: Double = 0.5,
    angle: Double = 0.0,
) {
    var position: Vec2 = position
    var velocity: Vec2 = velocity
    var mass: Double = mass
    var charge: Double = charge
    var size: Double = size
    var angle: Double = angle

    /** Last net force, used for the force overlay. */
    var force: Vec2 = Vec2.ZERO
    var age: Double = 0.0
    var faulted: Boolean = false

    /** Circle radius used for collisions (boxes use their circumscribed circle). */
    val collisionRadius: Double
        get() = if (shape == BodyShape.CIRCLE) size else size * sqrt(2.0)

    fun toDef(): BodyDef = BodyDef(
        id = id,
        position = position,
        velocity = velocity,
        mass = mass,
        charge = charge,
        shape = shape,
        size = size,
        angle = angle,
    )
}
