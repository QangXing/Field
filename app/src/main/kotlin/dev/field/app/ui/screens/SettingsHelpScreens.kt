package dev.field.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.AppPrefs
import dev.field.app.data.CrashLog
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
import dev.field.app.ui.theme.Surface
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary

@Composable
fun AppSettingsScreen(
    prefs: AppPrefs,
    onOpenBackground: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashLog.read(context)) }
    var showCrashLog by remember { mutableStateOf(false) }
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

            LabPanel(Modifier.fillMaxWidth(), accent = true) {
                SectionLabel("诊断")
                Text(
                    "若应用意外退出，最近一次崩溃堆栈会保存在本机。复制后可反馈给开发者定位问题。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
                val clipboard = LocalClipboardManager.current
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LabButton("复制崩溃日志", {
                        crashLog?.let { clipboard.setText(AnnotatedString(it)) }
                    }, enabled = crashLog != null, modifier = Modifier.weight(1f))
                    LabButton("清除", {
                        CrashLog.clear(context)
                        crashLog = null
                    }, enabled = crashLog != null, modifier = Modifier.weight(1f))
                }
                if (crashLog != null) {
                    Text(
                        crashLog!!,
                        color = TextPrimary,
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                        maxLines = 14,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        "暂无崩溃记录",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            LabPanel(Modifier.fillMaxWidth()) {
                SectionLabel("关于")
                KeyValueRow("应用", "Field Lab")
                KeyValueRow("版本", "0.3.1")
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
                    "磁场：方形区域内匀强 Bz（正值穿出屏幕，负值穿入屏幕），运动电荷受洛伦兹力。",
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
                    "顶栏「撤」/「重」撤销、重做场的放置与拖动，容量 20。",
                ),
            )
            HelpBlock(
                "场设置：中心、边框与形状",
                listOf(
                    "选中场后出现黄色虚线边框、中心圆点与边框手柄；圆形场显示虚线圆与圆周手柄。",
                    "拖动中心圆点：平移整个场；拖动边框/角点：改变边框尺寸（电场/磁场即作用范围）。",
                    "检查器可切换「方形 / 圆形」；圆形场以半径作为作用范围，边框外严格为零。",
                    "底部检查器同时提供中心 X/Y、边框宽/高（或半径）的滑杆，可精确输入。",
                ),
            )
            HelpBlock(
                "运动物体",
                listOf(
                    "小球为圆形、方块为方形；尺寸、质量、带电量、初速度都可在检查器中修改。",
                    "勾选世界规则后可显示重心十字、受力箭头、电流方向与轨迹预测。",
                    "「+」面板可批量发射探针或运动物体；物体与世界边界、墙、其他物体碰撞。",
                ),
            )
            HelpBlock(
                "手势与摄像机",
                listOf(
                    "选择与移动分离：点按只负责选中场/物体，不会因为拖动而误移动。",
                    "选中场后：拖动中心圆点平移，拖动边框或角点改大小；圆形场拖动圆周调整半径。",
                    "选中物体后：拖动其中心圆点移动；拖动其他空白区域平移世界。",
                    "双指捏合缩放，缩放锚定手指位置；存档设置可关闭滑动改变摄像机。",
                    "「设」打开世界设置：边界、积分器、14 项世界规则与显示设置。",
                    "设置页提供崩溃日志复制：若闪退，可在「设置 → 诊断」复制堆栈反馈。",
                    "「存」快速保存，「档」打开存档内操作（命名/保存/导出/导入）。",
                ),
            )
            HelpBlock(
                "存档与主页背景",
                listOf(
                    "存档页支持命名、查看（场景预览）、存档设置（世界规则 + 显示设置）。",
                    "世界规则与显示设置随 JSON 一起保存，不同存档可以有不同的物理规则。",
                    "设置 → 主页背景：可从任意存档的摄像机位置取景作为主页背景。",
                    "主页背景中的存档会实时运行：小球、方块与探针会继续移动。",
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
