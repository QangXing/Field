package dev.field.app.ui.transition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.AccentDark
import dev.field.app.ui.theme.AccentEdge

private val StripeColors = listOf(
    Accent,
    AccentDark,
    Color(0xFFE9BA63),
    Accent,
    Color(0xFFF0C56F),
    AccentDark,
)

/**
 * A full-screen yellow sheet sweeping left to right.
 *
 * At [progress] = 0.5 the sheet fully covers the viewport (and does so for a
 * short range around the midpoint), which is exactly when the app swaps pages.
 * A single continuous animation avoids the velocity stutter of a two-stage
 * wipe: the page changes underneath the fully covered screen.
 */
@Composable
fun YellowWipeOverlay(progress: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val skew = height * 0.22f
        val sheetWidth = width * 1.12f + skew * 2f
        val travel = width - skew + sheetWidth
        val left = skew - sheetWidth + progress.coerceIn(0f, 1f) * travel

        val sheet = Path().apply {
            moveTo(left, 0f)
            lineTo(left + sheetWidth, 0f)
            lineTo(left + sheetWidth - skew, height)
            lineTo(left - skew, height)
            close()
        }
        drawPath(sheet, Accent)

        clipPath(sheet) {
            // Internal diagonal seams give the sheet a block-built look without
            // ever exposing a gap in the coverage.
            val stripe = width / 4.2f
            var x = left - skew
            var index = 0
            while (x < left + sheetWidth + skew && index < 24) {
                val band = Path().apply {
                    moveTo(x, 0f)
                    lineTo(x + stripe * 0.45f, 0f)
                    lineTo(x + stripe * 0.45f - skew, height)
                    lineTo(x - skew, height)
                    close()
                }
                drawPath(band, StripeColors[index % StripeColors.size].copy(alpha = 0.55f))
                drawLine(
                    color = AccentEdge.copy(alpha = 0.55f),
                    start = Offset(x + stripe * 0.45f, 0f),
                    end = Offset(x + stripe * 0.45f - skew, height),
                    strokeWidth = 1.5f,
                )
                x += stripe
                index++
            }

            // Fine horizontal scan lines for texture.
            var y = 0f
            while (y < height) {
                drawLine(
                    color = AccentEdge.copy(alpha = 0.15f),
                    start = Offset(left - skew, y),
                    end = Offset(left + sheetWidth, y),
                    strokeWidth = 1f,
                )
                y += 12f
            }
        }

        // Bright leading edge at the top and a dark trailing edge at the bottom.
        drawLine(
            color = AccentEdge,
            start = Offset(left, 0f),
            end = Offset(left - skew, height),
            strokeWidth = 3f,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(left + sheetWidth - 2f, 0f),
            end = Offset(left + sheetWidth - skew - 2f, height),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        )
    }
}
