package dev.field.core.sim

import dev.field.core.math.Vec2

/**
 * A simulated particle. Mutable state is updated by [SimulationEngine] on the physics
 * hot path; the trail is a bounded FIFO of recent positions.
 */
class Probe(
    val id: Long,
    position: Vec2,
    velocity: Vec2,
    mass: Double = 1.0,
    charge: Double = 1.0,
    radius: Double = 0.12,
    dragScale: Double = 1.0,
) {
    var position: Vec2 = position
    var velocity: Vec2 = velocity
    var mass: Double = mass
    var charge: Double = charge
    var radius: Double = radius
    var dragScale: Double = dragScale

    /** Set when a non-finite state was detected; the probe is frozen at its last good position. */
    var faulted: Boolean = false

    private val trailBuffer = ArrayDeque<Vec2>(64)

    /** Snapshot of the trail, oldest first. */
    val trail: List<Vec2>
        get() = trailBuffer.toList()

    fun pushTrail(maxPoints: Int) {
        if (maxPoints <= 0) {
            trailBuffer.clear()
            return
        }
        trailBuffer.addLast(position)
        while (trailBuffer.size > maxPoints) {
            trailBuffer.removeFirst()
        }
    }
}
