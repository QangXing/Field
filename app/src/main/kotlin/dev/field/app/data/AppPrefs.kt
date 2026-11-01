package dev.field.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("field_lab", Context.MODE_PRIVATE)

    var reducedMotion by mutableStateOf(prefs.getBoolean(KEY_REDUCED_MOTION, false))
        private set

    /** "none", "demo", or a save file name. */
    var menuBackgroundSource by mutableStateOf(prefs.getString(KEY_BG_SOURCE, "none") ?: "none")
        private set

    var menuBackgroundX by mutableStateOf(prefs.getFloat(KEY_BG_X, 0f).toDouble())
        private set

    var menuBackgroundY by mutableStateOf(prefs.getFloat(KEY_BG_Y, 0f).toDouble())
        private set

    var menuBackgroundZoom by mutableStateOf(prefs.getFloat(KEY_BG_ZOOM, 34f).toDouble())
        private set

    var hideDefaultSaves by mutableStateOf(prefs.getBoolean(KEY_HIDE_DEFAULT_SAVES, false))
        private set

    fun updateReducedMotion(value: Boolean) {
        reducedMotion = value
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, value).apply()
    }

    fun updateMenuBackground(source: String, x: Double, y: Double, zoom: Double) {
        menuBackgroundSource = source
        menuBackgroundX = x
        menuBackgroundY = y
        menuBackgroundZoom = zoom
        prefs.edit()
            .putString(KEY_BG_SOURCE, source)
            .putFloat(KEY_BG_X, x.toFloat())
            .putFloat(KEY_BG_Y, y.toFloat())
            .putFloat(KEY_BG_ZOOM, zoom.toFloat())
            .apply()
    }

    fun updateHideDefaultSaves(value: Boolean) {
        hideDefaultSaves = value
        prefs.edit().putBoolean(KEY_HIDE_DEFAULT_SAVES, value).apply()
    }

    companion object {
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_BG_SOURCE = "menu_bg_source"
        private const val KEY_BG_X = "menu_bg_x"
        private const val KEY_BG_Y = "menu_bg_y"
        private const val KEY_BG_ZOOM = "menu_bg_zoom"
        private const val KEY_HIDE_DEFAULT_SAVES = "hide_default_saves"
    }
}
