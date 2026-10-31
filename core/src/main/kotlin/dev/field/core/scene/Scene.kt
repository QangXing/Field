package dev.field.core.scene

import dev.field.core.fields.FieldSource
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.sim.Boundary
import dev.field.core.sim.ProbeDef
import dev.field.core.sim.SimSettings
import dev.field.core.viz.HeatmapMode
import kotlinx.serialization.Serializable

@Serializable
data class CameraDef(
    val center: Vec2,
    val zoom: Double = 24.0,
)

@Serializable
data class VisualDef(
    val arrows: Boolean = true,
    val streamlines: Boolean = true,
    val heatmap: Boolean = false,
    val trails: Boolean = true,
    val heatmapMode: HeatmapMode = HeatmapMode.POTENTIAL,
)

@Serializable
data class WorldDef(
    val bounds: Aabb,
    val boundary: Boundary = Boundary.REFLECT,
    val gravity: Vec2 = Vec2.ZERO,
)

@Serializable
data class Scene(
    val version: Int = 2,
    val name: String,
    val world: WorldDef,
    val settings: SimSettings,
    val sources: List<FieldSource>,
    val probes: List<ProbeDef>,
    val camera: CameraDef,
    val visuals: VisualDef,
)
