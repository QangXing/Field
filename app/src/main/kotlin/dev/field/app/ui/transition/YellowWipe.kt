package dev.field.app.ui.transition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.AccentDark
import dev.field.app.ui.theme.AccentEdge

/**
 * Six skewed yellow blocks sweeping across the screen. At [progress] = 0.5 the
 * screen is fully covered, which is where the app swaps pages.
 */
@Composable
fun YellowWipeOverlay(progress: Float) {
    val colors = listOf(Accent, AccentDark, Accent.copy(red = 0.9f, green = 0.65f, blue = 0.24f))
    Canvas(Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val blockWidth = width / 6f * 1.5f
        val skew = height * 0.35f
        val travel = 3f * width
        val offset = (progress - 0.5f) * travel

        for (index in 0 until 6) {
            val left = index * width / 6f + offset
            val top = 0f
            val bottom = height

            val path = Path().apply {
                moveTo(left, top)
                lineTo(left + blockWidth, top)
                lineTo(left + blockWidth - skew, bottom)
                lineTo(left - skew, bottom)
                close()
            }
            drawPath(path, colors[index % colors.size])
            drawPath(path, AccentEdge.copy(alpha = 0.7f), style = Stroke(width = 2f))
        }

        // Thin leading edge highlight for a sense of motion.
        drawLine(
            color = Accent.copy(alpha = 0.85f),
            start = androidx.compose.ui.geometry.Offset(offset + width * 0.5f - skew, 0f),
            end = androidx.compose.ui.geometry.Offset(offset + width * 0.5f, height),
            strokeWidth = 2f,
            cap = StrokeCap.Square,
        )
    }
}
