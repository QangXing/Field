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
import dev.field.app.ui.components.LabButton
import dev.field.app.ui.components.LabIconButton
import dev.field.app.ui.components.LabPanel
import dev.field.app.ui.components.LabToggle
import dev.field.app.ui.components.SectionLabel
import dev.field.app.ui.components.TitleText
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.TextSecondary

@Composable
fun AppSettingsScreen(
    prefs: AppPrefs,
    onOpenBackground: () -> Unit,
    onBack: () -> Unit,
) {
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

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabPanel(Modifier.fillMaxWidth(), accent = true) {
                SectionLabel("主页")
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(
                        "背景可以取任意存档的摄像机画面：选择来源后调整中心与缩放。",
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    LabButton("主页背景取景", onOpenBackground, accent = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
            }

            LabPanel(Modifier.fillMaxWidth(), accent = true) {
                SectionLabel("界面")
                LabToggle("减弱动画（关闭黄色滑块转场）", prefs.reducedMotion, { prefs.updateReducedMotion(it) })
                Text(
                    "转场是一整块黄色斜切滑块：从左滑入、完全覆盖屏幕后继续向右滑出，覆盖瞬间切换页面。开启后直接切换。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
            }

            LabPanel(Modifier.fillMaxWidth()) {
                SectionLabel("关于")
                KeyValueRow("应用", "Field Lab")
                KeyValueRow("版本", "0.2.2")
                KeyValueRow("平台", "Android 8.0+ · 竖屏")
                KeyValueRow("引擎", "自研确定性场模拟")
                KeyValueRow("权限", "无敏感权限")
                DividerLine()
                Text(
                    "仿真单位为无量纲\"模拟单位\"：kE = kM = G = 1。电场/磁场为方形匀强区域，边框外为零。",
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

        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HelpBlock(
                "七类场与运动物体",
                listOf(
                    "电场：方形区域内匀强电场，强度与方向可调，边框外为零；带电探针与物体受力。",
                    "磁场：方形区域内匀强 Bz（正值 ⊙ 出屏 / 负值 ⊗ 入屏），运动电荷受洛伦兹力。",
                    "重力场：矩形区域内恒定加速度，方向由旋转决定。",
                    "引力场：质量体产生平方反比引力，G = 1，受「万有引力」规则控制。",
                    "阻力场：区域内线性 + 二次阻力，可设置风向。",
                    "均匀力场：区域内任意方向的常加速度，用于风/斜面实验。",
                    "墙：静态碰撞段；反射或消散行为受世界规则控制。",
                    "小球/方块：有质量、带电量、速度与尺寸，可显示重心与受力。",
                ),
            )
            HelpBlock(
                "放置玩法（仿 Mindustry）",
                listOf(
                    "底部工具带选择场单元；再次点击同一槽位取消，松开后回到选择模式。",
                    "单击地图放置 1 个单元；按住拖动会沿水平/垂直方向按足迹间距连续放置。",
                    "幽灵预览：可放置为黄色描边，非法为红色；失败原因以顶部提示显示。",
                    "橡皮固定在工具带最右端，点击或拖过即删除（场与物体都能删）。",
                    "顶栏 ↶ / ↷ 撤销、重做场的放置与拖动，容量 20。",
                ),
            )
            HelpBlock(
                "场设置：中心与边框",
                listOf(
                    "选中场后出现黄色虚线边框、中心圆点与右上角方形手柄。",
                    "拖动中心圆点：平移整个场；拖动方形手柄：改变边框尺寸（电场/磁场即作用范围）。",
                    "电场/磁场只有边框内的空间受场影响，边框外严格为零；矩形场的边框即作用区域。",
                    "底部检查器同时提供中心 X/Y 与边框宽/高的滑杆，可精确输入。",
                ),
            )
            HelpBlock(
                "运动物体",
                listOf(
                    "小球为圆形、方块为方形；尺寸、质量、带电量、初速度都可在检查器中修改。",
                    "勾选世界规则后可显示重心十字、受力箭头、电流方向与轨迹预测。",
                    "「＋」面板可批量发射探针或运动物体；物体与世界边界、墙、其他物体碰撞。",
                ),
            )
            HelpBlock(
                "手势与摄像机",
                listOf(
                    "选择模式：点击选中场/物体；拖动移动；拖动空白平移世界。",
                    "双指捏合缩放，缩放锚定手指位置；存档设置可关闭滑动改变摄像机。",
                    "「设」打开世界设置：边界、积分器、14 项世界规则与显示设置。",
                    "「存」快速保存，「档」打开存档内操作（命名/保存/导出/导入）。",
                ),
            )
            HelpBlock(
                "存档与主页背景",
                listOf(
                    "存档页支持命名、查看（场景预览）、存档设置（世界规则 + 显示设置）。",
                    "世界规则与显示设置随 JSON 一起保存，不同存档可以有不同的物理规则。",
                    "设置 → 主页背景：可从任意存档的摄像机位置取景作为主页背景。",
                    "场景为 JSON v3（.field.json），每 15 秒自动保存，导出/导入不需要存储权限。",
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
