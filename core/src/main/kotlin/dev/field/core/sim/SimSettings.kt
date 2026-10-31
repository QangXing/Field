package dev.field.core.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Boundary {
    @SerialName("reflect")
    REFLECT,

    @SerialName("wrap")
    WRAP,

    @SerialName("open")
    OPEN,
}

@Serializable
enum class Integrator {
    @SerialName("semiImplicit")
    SEMI_IMPLICIT,

    @SerialName("rk4")
    RK4,
}

@Serializable
data class SimSettings(
    val fixedDt: Double = 1.0 / 60.0,
    val subSteps: Int = 2,
    val integrator: Integrator = Integrator.SEMI_IMPLICIT,
    val speed: Double = 1.0,
    val collisions: Boolean = true,
    val gridSnap: Double = 0.5,
)
