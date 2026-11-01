package dev.field.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.ui.canvas.ScenePreview
import dev.field.app.ui.components.LabButton
import dev.field.app.ui.components.TitleText
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.AccentDark
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Border
import dev.field.app.ui.theme.GridLine
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.math.Vec2
import dev.field.core.scene.Scene

@Composable
fun MenuScreen(
    hasSession: Boolean,
    background: Scene?,
    backgroundCenter: Vec2,
    backgroundZoom: Double,
    onContinue: () -> Unit,
    onNewWorld: () -> Unit,
    onDemo: () -> Unit,
    onSaves: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (background != null) {
            ScenePreview(
                scene = background,
                center = backgroundCenter,
                zoom = backgroundZoom,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.36f)))
        } else {
            MenuBackground(Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "FIELD LAB // 2D",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    "v0.2.0",
                    color = Accent,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(28.dp))
            LogoMark()
            Spacer(Modifier.height(10.dp))
            TitleText("FIELD LAB", color = TextPrimary)
            Text(
                "2D 场模拟沙盒 · 电场 / 磁场 / 重力 / 引力 / 阻力",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            Column(
                Modifier.fillMaxWidth().width(320.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (hasSession) {
                    LabButton("继续上次世界", onContinue, accent = true, modifier = Modifier.fillMaxWidth())
                }
                LabButton("新建空世界", onNewWorld, modifier = Modifier.fillMaxWidth())
                LabButton("打开演示世界", onDemo, modifier = Modifier.fillMaxWidth())
                LabButton("存档", onSaves, modifier = Modifier.fillMaxWidth())
                LabButton("设置", onSettings, modifier = Modifier.fillMaxWidth())
                LabButton("帮助", onHelp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "无网络权限 · 场景以 JSON 保存在本地 · 存档可作主页背景",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Thin Mindustry-style corner frame.
        Canvas(Modifier.fillMaxSize()) {
            val corner = 18f
            val accent = Accent.copy(alpha = 0.35f)
            drawLine(accent, Offset(0f, corner), Offset(0f, 0f), 2f)
            drawLine(accent, Offset(0f, 0f), Offset(corner, 0f), 2f)
            drawLine(accent, Offset(size.width - corner, 0f), Offset(size.width, 0f), 2f)
            drawLine(accent, Offset(size.width, 0f), Offset(size.width, corner), 2f)
            drawLine(accent, Offset(0f, size.height - corner), Offset(0f, size.height), 2f)
            drawLine(accent, Offset(0f, size.height), Offset(corner, size.height), 2f)
            drawLine(accent, Offset(size.width - corner, size.height), Offset(size.width, size.height), 2f)
            drawLine(accent, Offset(size.width, size.height), Offset(size.width, size.height - corner), 2f)
        }
    }
}

@Composable
private fun LogoMark() {
    Canvas(Modifier.size(width = 132.dp, height = 56.dp)) {
        val h = size.height
        val w = size.width
        val blocks = listOf(
            Triple(0.10f, 0.22f, Accent),
            Triple(0.36f, 0.28f, AccentDark),
            Triple(0.62f, 0.22f, Accent.copy(alpha = 0.75f)),
        )
        for ((xFraction, widthFraction, color) in blocks) {
            val left = w * xFraction
            val blockWidth = w * widthFraction
            val skew = h * 0.32f
            val path = Path().apply {
                moveTo(left, 0f)
                lineTo(left + blockWidth, 0f)
                lineTo(left + blockWidth - skew, h)
                lineTo(left - skew, h)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun MenuBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 56f
        var x = 0f
        while (x < size.width) {
            drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(GridLine, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val path = Path().apply {
            moveTo(size.width * 0.62f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.55f)
            lineTo(size.width * 0.45f, size.height * 0.55f)
            close()
        }
        drawPath(path, Accent.copy(alpha = 0.05f))
        drawLine(
            Border.copy(alpha = 0.5f),
            Offset(0f, size.height * 0.92f),
            Offset(size.width, size.height * 0.92f),
            strokeWidth = 1f,
        )
        drawLine(
            Accent.copy(alpha = 0.16f),
            Offset(0f, size.height * 0.18f),
            Offset(size.width * 0.72f, size.height * 0.18f),
            strokeWidth = 1f,
        )
    }
}
