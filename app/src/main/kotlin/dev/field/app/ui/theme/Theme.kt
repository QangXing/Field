package dev.field.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

val Bg = Color(0xFF171A1E)
val Surface = Color(0xFF262B31)
val SurfaceRaised = Color(0xFF313840)
val Border = Color(0xFF48515B)
val Accent = Color(0xFFFFD37F)
val AccentDark = Color(0xFFD9A94B)
val AccentEdge = Color(0xFF8A6420)
val TextPrimary = Color(0xFFE8EDF2)
val TextSecondary = Color(0xFF98A3AE)
val Danger = Color(0xFFFF6B5E)
val GridLine = Color(0xFF2B3036)
val GridLineMajor = Color(0xFF3A424B)

val FieldElectric = Color(0xFF4FD1FF)
val FieldMagnetic = Color(0xFFFF7BD5)
val FieldGravity = Color(0xFFFFD37F)
val FieldDrag = Color(0xFF7EE787)
val FieldForce = Color(0xFFC792EA)

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

private val LabTypography = Typography().let { base ->
    Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.Monospace),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Monospace),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Monospace),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Monospace),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
fun FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = LabTypography,
        content = content,
    )
}
