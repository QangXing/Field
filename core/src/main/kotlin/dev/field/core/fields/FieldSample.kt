package dev.field.core.fields

import dev.field.core.math.Vec2

/**
 * Accumulated field quantities at one world position.
 *
 * Instances are reused on the physics hot path; call [reset] before re-accumulating.
 */
data class FieldSample(
    var electric: Vec2 = Vec2.ZERO,
    var magneticBz: Double = 0.0,
    var gravity: Vec2 = Vec2.ZERO,
    var dragLinear: Double = 0.0,
    var dragQuadratic: Double = 0.0,
    var wind: Vec2 = Vec2.ZERO,
    var uniform: Vec2 = Vec2.ZERO,
    var potential: Double = 0.0,
) {
    fun reset() {
        electric = Vec2.ZERO
        magneticBz = 0.0
        gravity = Vec2.ZERO
        dragLinear = 0.0
        dragQuadratic = 0.0
        wind = Vec2.ZERO
        uniform = Vec2.ZERO
        potential = 0.0
    }
}
