package dev.field.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.AppPrefs
import dev.field.app.data.Defaults
import dev.field.app.data.SaveRepository
import dev.field.app.ui.canvas.ScenePreview
import dev.field.app.ui.components.Chip
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
import dev.field.app.ui.theme.Border
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.Surface
import dev.field.app.ui.theme.SurfaceRaised
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.scene.Scene
import dev.field.core.sim.WorldRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavesScreen(
    repo: SaveRepository,
    prefs: AppPrefs,
    onBack: () -> Unit,
    onOpen: (Scene) -> Unit,
) {
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Scene?>(null) }
    var renameTarget by remember { mutableStateOf<SaveRepository.Entry?>(null) }
    var settingsTarget by remember { mutableStateOf<SaveRepository.Entry?>(null) }

    val entries = remember(refresh) { repo.list() }
    val autosave = remember(refresh) { repo.loadAutosave() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            repo.import(uri)
                .onSuccess { scene ->
                    repo.save(scene)
                    message = "已导入：${scene.name}"
                    refresh++
                }
                .onFailure { message = "导入失败：${it.message}" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabIconButton("←", onBack, modifier = Modifier.width(38.dp))
            TitleText("存档")
            Spacer(Modifier.weight(1f))
            LabButton("导入", { importLauncher.launch(arrayOf("*/*")) })
        }

        message?.let {
            Text(it, color = Accent, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (autosave != null) {
                item {
                    SaveCard(
                        title = "自动存档",
                        detail = "场 ${autosave.sources.size} · 物体 ${autosave.bodies.size} · 规则 ${activeRuleCount(autosave.rules)}",
                        highlighted = true,
                        onOpen = { onOpen(autosave) },
                        onPreview = { preview = autosave },
                        onSettings = null,
                        onRename = null,
                        onDelete = null,
                    )
                }
            }

            if (!prefs.hideDefaultSaves) {
                item {
                    val demo = Defaults.demoScene()
                    SaveCard(
                        title = "内置演示世界（只读）",
                        detail = "场 ${demo.sources.size} · 物体 ${demo.bodies.size} · 规则 ${activeRuleCount(demo.rules)}",
                        highlighted = false,
                        onOpen = { onOpen(demo) },
                        onPreview = { preview = demo },
                        onSettings = null,
                        onRename = null,
                        onDelete = null,
                    )
                }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "还没有存档。在世界内点击「存」即可保存。",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            items(entries, key = { it.file.absolutePath }) { entry ->
                SaveCard(
                    title = entry.name,
                    detail = formatTime(entry.modified) + " · 点「查看」预览",
                    highlighted = false,
                    onOpen = {
                        repo.load(entry.file)
                            .onSuccess { onOpen(it) }
                            .onFailure { message = "载入失败：${it.message}" }
                    },
                    onPreview = {
                        repo.load(entry.file)
                            .onSuccess { preview = it }
                            .onFailure { message = "读取失败：${it.message}" }
                    },
                    onSettings = { settingsTarget = entry },
                    onRename = { renameTarget = entry },
                    onDelete = {
                        repo.delete(entry.file)
                        message = "已删除：${entry.name}"
                        refresh++
                    },
                )
            }
        }
    }

    preview?.let { scene ->
        ModalPanel(onDismiss = { preview = null }) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                SectionLabel("存档查看 · ${scene.name}")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .border(1.dp, Border)
                        .background(Bg),
                ) {
                    ScenePreview(
                        scene = scene,
                        center = scene.camera.center,
                        zoom = scene.camera.zoom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                KeyValueRow("场源", "${scene.sources.size} 个")
                KeyValueRow("运动物体", "${scene.bodies.size} 个")
                KeyValueRow("探针", "${scene.probes.size} 个")
                KeyValueRow("边界", boundaryLabel(scene))
                KeyValueRow("规则", "${activeRuleCount(scene.rules)} 项开启")
                KeyValueRow("格式", "JSON v${scene.version}")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    LabButton("载入此存档", {
                        preview = null
                        onOpen(scene)
                    }, accent = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    LabButton("关闭", { preview = null }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    renameTarget?.let { entry ->
        var name by remember(entry) { mutableStateOf(entry.name) }
        ModalPanel(onDismiss = { renameTarget = null }) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("存档命名")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip("名称", true)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .background(SurfaceRaised)
                            .border(1.dp, Border)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabButton("重命名", {
                        repo.rename(entry.file, name)
                            .onSuccess {
                                message = "已重命名为：$name"
                                refresh++
                                renameTarget = null
                            }
                            .onFailure { message = "重命名失败：${it.message}" }
                    }, accent = true, modifier = Modifier.weight(1f))
                    LabButton("取消", { renameTarget = null }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    settingsTarget?.let { entry ->
        val loaded = remember(entry) { repo.load(entry.file).getOrNull() }
        if (loaded == null) {
            ModalPanel(onDismiss = { settingsTarget = null }) {
                Column(Modifier.padding(12.dp)) {
                    Text("存档读取失败", color = Danger, fontSize = 12.sp)
                    LabButton("关闭", { settingsTarget = null })
                }
            }
        } else {
            var rules by remember(entry) { mutableStateOf(loaded.rules) }
            var lockCamera by remember(entry) { mutableStateOf(loaded.display.lockCamera) }
            var hideDefaults by remember(entry) {
                mutableStateOf(loaded.display.hideDefaults || prefs.hideDefaultSaves)
            }
            ModalPanel(onDismiss = { settingsTarget = null }) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    SectionLabel("存档设置 · ${entry.name}")
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    ) {
                        SectionLabel("场设置（世界规则）")
                        RuleRow("万有引力", rules.gravityEnabled) { rules = rules.copy(gravityEnabled = it) }
                        RuleRow("带电物体间电荷传递", rules.chargeTransfer) { rules = rules.copy(chargeTransfer = it) }
                        RuleRow("无视反弹", rules.ignoreBounce) { rules = rules.copy(ignoreBounce = it) }
                        RuleRow("带电体漏电", rules.chargeLeak) { rules = rules.copy(chargeLeak = it) }
                        RuleRow("粒子运动轨迹预测", rules.trajectoryPrediction) { rules = rules.copy(trajectoryPrediction = it) }
                        RuleRow("粒子受力及力矩显示", rules.forceTorqueDisplay) { rules = rules.copy(forceTorqueDisplay = it) }
                        RuleRow("带电体间能力影响（库仑力）", rules.chargeForceEnabled) { rules = rules.copy(chargeForceEnabled = it) }
                        RuleRow("带电体受力显示", rules.chargeForceDisplay) { rules = rules.copy(chargeForceDisplay = it) }
                        RuleRow("电流方向显示", rules.currentDirection) { rules = rules.copy(currentDirection = it) }
                        RuleRow("粒子撞到物体后消散", rules.despawnOnImpact) { rules = rules.copy(despawnOnImpact = it) }
                        RuleRow("粒子存活寿命", rules.particleLifetime) { rules = rules.copy(particleLifetime = it) }
                        RuleRow("粒子运动轨迹线", rules.trails) { rules = rules.copy(trails = it) }
                        RuleRow("场的箭头显示", rules.fieldArrows) { rules = rules.copy(fieldArrows = it) }
                        RuleRow("重心显示", rules.centerOfGravity) { rules = rules.copy(centerOfGravity = it) }
                        DividerLine()
                        SectionLabel("显示设置")
                        RuleRow("隐藏默认案例（全局）", hideDefaults) { hideDefaults = it }
                        RuleRow("关闭滑动改变摄像机参数", lockCamera) { lockCamera = it }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LabButton("保存设置", {
                            repo.update(entry.file) { scene ->
                                scene.copy(
                                    rules = rules,
                                    display = scene.display.copy(
                                        lockCamera = lockCamera,
                                        hideDefaults = hideDefaults,
                                    ),
                                )
                            }.onSuccess {
                                prefs.updateHideDefaultSaves(hideDefaults)
                                message = "已保存世界规则：${entry.name}"
                                refresh++
                                settingsTarget = null
                            }.onFailure { message = "保存失败：${it.message}" }
                        }, accent = true, modifier = Modifier.weight(1f))
                        LabButton("取消", { settingsTarget = null }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveCard(
    title: String,
    detail: String,
    highlighted: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onSettings: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    LabPanel(Modifier.fillMaxWidth(), accent = highlighted) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = if (highlighted) Accent else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        detail,
                        color = TextSecondary,
                        fontSize = 9.sp,
                    )
                }
                LabButton("载入", onOpen, accent = true)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SmallAction("查看", onPreview)
                onSettings?.let { SmallAction("设置", it) }
                onRename?.let { SmallAction("改名", it) }
                onDelete?.let { SmallAction("删除", it, danger = true) }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier
            .height(30.dp)
            .background(SurfaceRaised)
            .border(1.dp, Border)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (danger) Danger else TextPrimary, fontSize = 10.sp)
    }
}

@Composable
private fun RuleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabToggle(label, checked, onChange)
}

@Composable
private fun ModalPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clickable { }
                .padding(horizontal = 10.dp),
        ) {
            LabPanel(Modifier.fillMaxSize(), accent = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
    }
}

private fun boundaryLabel(scene: Scene): String = when (scene.world.boundary) {
    dev.field.core.sim.Boundary.REFLECT -> "反射"
    dev.field.core.sim.Boundary.WRAP -> "环绕"
    dev.field.core.sim.Boundary.OPEN -> "飞出"
}

private fun activeRuleCount(rules: WorldRules): Int {
    var count = 0
    if (rules.gravityEnabled) count++
    if (rules.chargeTransfer) count++
    if (rules.ignoreBounce) count++
    if (rules.chargeLeak) count++
    if (rules.trajectoryPrediction) count++
    if (rules.forceTorqueDisplay) count++
    if (rules.chargeForceEnabled) count++
    if (rules.chargeForceDisplay) count++
    if (rules.currentDirection) count++
    if (rules.despawnOnImpact) count++
    if (rules.particleLifetime) count++
    if (rules.trails) count++
    if (rules.fieldArrows) count++
    if (rules.centerOfGravity) count++
    return count
}

private fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
