package dev.field.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.AppPrefs
import dev.field.app.ui.components.DividerLine
import dev.field.app.ui.components.KeyValueRow
import dev.field.app.ui.components.LabIconButton
import dev.field.app.ui.components.LabPanel
import dev.field.app.ui.components.LabToggle
import dev.field.app.ui.components.SectionLabel
import dev.field.app.ui.components.TitleText
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary

@Composable
fun AppSettingsScreen(prefs: AppPrefs, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabIconButton("←", onBack)
            TitleText("设置")
        }

        Spacer(Modifier.height(20.dp))

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabPanel(Modifier.fillMaxWidth(), accent = true) {
                SectionLabel("界面")
                LabToggle("减弱动画（关闭黄色斜块转场）", prefs.reducedMotion, { prefs.updateReducedMotion(it) })
                Text(
                    "转场由 6 块黄色斜块扫过屏幕，在 50% 处切换页面。开启后直接切换。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
            }

            LabPanel(Modifier.fillMaxWidth()) {
                SectionLabel("关于")
                KeyValueRow("应用", "Field Lab")
                KeyValueRow("版本", "0.1.0")
                KeyValueRow("平台", "Android 8.0+")
                KeyValueRow("引擎", "自研确定性场模拟")
                KeyValueRow("权限", "无敏感权限")
                DividerLine()
                Text(
                    "仿真单位为无量纲\"模拟单位\"：kE = kM = G = 1。磁场采用双磁极近似，仅用于演示。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabIconButton("←", onBack)
            TitleText("帮助")
        }

        Spacer(Modifier.height(20.dp))

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HelpBlock(
                "五类场",
                listOf(
                    "电场：点电荷产生 E = kΣq r̂ / r²，带电探针受力。正电荷青色，负电荷红色。",
                    "磁场：双磁极近似，Bz 垂直屏幕，探针受洛伦兹力。磁力线品红。",
                    "重力场：矩形区域内恒定加速度，方向由旋转决定。",
                    "引力场：质量体产生平方反比引力，G = 1。",
                    "阻力场：区域内线性 + 二次阻力，可设置风向。",
                    "均匀力场：区域内任意方向的常加速度，用于风/斜面实验。",
                    "墙：静态碰撞段，探针与墙弹性碰撞。",
                ),
            )
            HelpBlock(
                "放置玩法（类似 Mindustry）",
                listOf(
                    "点击底部槽位进入放置模式，出现半透明幽灵预览；合法为黄色描边，非法为红色。",
                    "单击空白放置 1 个场单元，立即落位并自动选中。",
                    "按住拖动：方向锁定为水平或垂直，按足迹 + 0.5 m 间距连续放置。",
                    "可旋转单元用工具带右侧「↻」按钮 +45°；点击角度标签归零。",
                    "橡皮：点击或拖过场单元即删除，整个手势记为一次撤销。",
                    "顶栏 ↶ / ↷ 撤销、重做，容量 20。",
                ),
            )
            HelpBlock(
                "手势",
                listOf(
                    "选择模式：点击场单元选中；拖动移动；拖动空白平移世界。",
                    "双指捏合缩放，缩放锚定手指位置。",
                    "顶部「＋」打开探针发射面板；「设」打开世界与可视化设置。",
                    "「存」快速保存，「档」打开存档内操作（保存/导出/导入）。",
                ),
            )
            HelpBlock(
                "存档格式",
                listOf(
                    "场景为 JSON v2（.field.json），保存于应用私有目录，每 15 秒自动保存。",
                    "导出/导入使用系统文件选择器，不需要任何存储权限。",
                ),
            )
        }
    }
}

@Composable
private fun HelpBlock(title: String, lines: List<String>) {
    LabPanel(Modifier.fillMaxWidth(), accent = true) {
        SectionLabel(title)
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { line ->
                Row {
                    Text("·", color = Accent, fontSize = 12.sp, modifier = Modifier.width(14.dp), fontWeight = FontWeight.Bold)
                    Text(line, color = TextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
