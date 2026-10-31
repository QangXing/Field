package dev.field.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF171A1E)
val Surface = Color(0xFF262B31)
val SurfaceRaised = Color(0xFF313840)
val Border = Color(0xFF48515B)
val Accent = Color(0xFFFFD37F)
val AccentDark = Color(0xFFD9A94B)
val TextPrimary = Color(0xFFE8EDF2)
val TextSecondary = Color(0xFF98A3AE)
val Danger = Color(0xFFFF6B5E)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    secondary = AccentDark,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    error = Danger,
)

@Composable
fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
