package dev.field.core.scene

import dev.field.core.fields.FieldSource
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.sim.Boundary
import dev.field.core.sim.BodyDef
import dev.field.core.sim.ProbeDef
import dev.field.core.sim.SimSettings
import dev.field.core.sim.WorldRules
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

/** Per-save display preferences. */
@Serializable
data class DisplaySettings(
    /** 隐藏默认案例/预设（内置演示与快捷预设不显示）。 */
    val hideDefaults: Boolean = false,

    /** 关闭滑动改变摄像机参数（禁用平移与缩放手势）。 */
    val lockCamera: Boolean = false,
)

@Serializable
data class Scene(
    val version: Int = 3,
    val name: String,
    val world: WorldDef,
    val settings: SimSettings,
    val sources: List<FieldSource>,
    val probes: List<ProbeDef>,
    val camera: CameraDef,
    val visuals: VisualDef,
    // v3 fields are appended with defaults so v2 files still decode.
    val bodies: List<BodyDef> = emptyList(),
    val rules: WorldRules = WorldRules(),
    val display: DisplaySettings = DisplaySettings(),
)
