package dev.field.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.AppPrefs
import dev.field.app.data.SaveRepository
import dev.field.app.ui.canvas.ScenePreview
import dev.field.app.ui.components.Chip
import dev.field.app.ui.components.LabButton
import dev.field.app.ui.components.LabIconButton
import dev.field.app.ui.components.LabPanel
import dev.field.app.ui.components.LabSlider
import dev.field.app.ui.components.SectionLabel
import dev.field.app.ui.components.TitleText
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.math.Vec2
import dev.field.core.scene.Scene

@Composable
fun MenuBackgroundScreen(
    repo: SaveRepository,
    prefs: AppPrefs,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    var source by remember { mutableStateOf(prefs.menuBackgroundSource) }
    var centerX by remember { mutableStateOf(prefs.menuBackgroundX) }
    var centerY by remember { mutableStateOf(prefs.menuBackgroundY) }
    var zoom by remember { mutableStateOf(prefs.menuBackgroundZoom) }
    var message by remember { mutableStateOf<String?>(null) }

    val entries = remember { repo.list() }
    val scene: Scene? = remember(source) {
        when (source) {
            "none" -> null
            "demo" -> dev.field.app.data.Defaults.demoScene()
            else -> repo.loadNamed(source)
        }
    }

    val bounds = scene?.world?.bounds

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabIconButton("←", onBack)
            TitleText("主页背景")
            Spacer(Modifier.weight(1f))
            LabButton("应用", {
                prefs.updateMenuBackground(source, centerX, centerY, zoom)
                onApplied()
                message = "已应用到主页背景"
            }, accent = true)
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(Bg)) {
            if (scene != null) {
                ScenePreview(
                    scene = scene,
                    center = Vec2(centerX, centerY),
                    zoom = zoom,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Bg.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "取景预览 · 背景会被主页遮罩压暗",
                        color = TextSecondary,
                        fontSize = 9.sp,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未选择背景：主页使用默认网格背景", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        LabPanel(Modifier.fillMaxWidth(), accent = true) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SectionLabel("选择画面来源")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip("默认网格", source == "none", Modifier.clickable {
                        source = "none"
                        message = null
                    })
                    Chip("内置演示", source == "demo", Modifier.clickable {
                        source = "demo"
                        message = null
                    })
                    entries.forEach { entry ->
                        Chip(entry.name, source == entry.file.name, Modifier.clickable {
                            source = entry.file.name
                            val loaded = repo.loadNamed(entry.file.name)
                            if (loaded == null) {
                                message = "该存档无法读取"
                            } else {
                                message = null
                                val c = loaded.camera.center
                                centerX = c.x
                                centerY = c.y
                                zoom = loaded.camera.zoom
                            }
                        })
                    }
                }

                if (scene != null && bounds != null) {
                    SectionLabel("摄像机取景")
                    LabSlider(
                        "中心 X",
                        centerX,
                        bounds.minX..bounds.maxX,
                        onValueChange = { centerX = it },
                        valueText = "%.1f m".format(centerX),
                    )
                    LabSlider(
                        "中心 Y",
                        centerY,
                        bounds.minY..bounds.maxY,
                        onValueChange = { centerY = it },
                        valueText = "%.1f m".format(centerY),
                    )
                    LabSlider(
                        "缩放",
                        zoom,
                        6.0..120.0,
                        onValueChange = { zoom = it },
                        valueText = "%.0f px/m".format(zoom),
                    )
                    Text(
                        "提示：先在某存档里把镜头对准想拍摄的位置，再到此页读取它的取景即可。",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                message?.let {
                    Text(
                        it,
                        color = Accent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
