package dev.field.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last uncaught exception so the user can share it from the
 * in-app diagnostics panel without needing adb.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Field Lab crash")
                    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    appendLine("thread: ${thread.name}")
                    appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API ${android.os.Build.VERSION.SDK_INT}")
                    appendLine()
                    append(error.stackTraceToString())
                }
                File(appContext.filesDir, FILE_NAME).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
