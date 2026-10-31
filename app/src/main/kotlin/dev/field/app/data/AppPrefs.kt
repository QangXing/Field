package dev.field.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("field_lab", Context.MODE_PRIVATE)

    var reducedMotion by mutableStateOf(prefs.getBoolean(KEY_REDUCED_MOTION, false))
        private set

    fun updateReducedMotion(value: Boolean) {
        reducedMotion = value
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, value).apply()
    }

    companion object {
        private const val KEY_REDUCED_MOTION = "reduced_motion"
    }
}
