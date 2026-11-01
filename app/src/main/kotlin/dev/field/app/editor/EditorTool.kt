package dev.field.app.editor

import androidx.annotation.DrawableRes
import dev.field.app.R

/** The Mindustry-style bottom toolbar slots. `tool == null` means SELECT mode. */
enum class EditorTool(
    val label: String,
    @DrawableRes val iconRes: Int,
) {
    SELECT("选择", R.drawable.tool_select),
    ELECTRIC("电场", R.drawable.field_electric),
    MAGNETIC("磁场", R.drawable.field_magnetic),
    GRAVITY("重力", R.drawable.field_gravity),
    GRAVITATIONAL("引力", R.drawable.field_gravitational),
    DRAG("阻力", R.drawable.field_drag),
    FORCE("均匀力", R.drawable.field_uniform_force),
    WALL("墙", R.drawable.tool_wall),
    BALL("小球", R.drawable.tool_ball),
    BLOCK("方块", R.drawable.tool_block),
    ERASE("橡皮", R.drawable.tool_eraser);

    val isEraser: Boolean get() = this == ERASE

    val isBody: Boolean get() = this == BALL || this == BLOCK

    /** Point units have no footprint and snap to the grid. */
    val pointLike: Boolean get() = this == ELECTRIC || this == MAGNETIC || this == GRAVITATIONAL

    val rotatable: Boolean
        get() = this == MAGNETIC || this == GRAVITY || this == DRAG ||
            this == FORCE || this == WALL || this == BALL || this == BLOCK

    companion object {
        /** The slots shown in the bottom toolbar (select is represented separately). */
        val placementSlots: List<EditorTool> = entries.filter { it != SELECT }
    }
}
