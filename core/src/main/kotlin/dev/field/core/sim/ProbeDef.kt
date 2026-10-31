package dev.field.core.sim

import dev.field.core.math.Vec2
import kotlinx.serialization.Serializable

@Serializable
data class ProbeDef(
    val id: Long = 0,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val mass: Double = 1.0,
    val charge: Double = 1.0,
    val radius: Double = 0.12,
)
