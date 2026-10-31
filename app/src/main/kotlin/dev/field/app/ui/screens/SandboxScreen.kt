package dev.field.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.SaveRepository
import dev.field.app.editor.EditorState
import dev.field.app.editor.EditorTool
import dev.field.app.ui.canvas.FieldCanvas
import dev.field.app.ui.components.Chip
import dev.field.app.ui.components.DividerLine
import dev.field.app.ui.components.KeyValueRow
import dev.field.app.ui.components.LabButton
import dev.field.app.ui.components.LabIconButton
import dev.field.app.ui.components.LabPanel
import dev.field.app.ui.components.LabSlider
import dev.field.app.ui.components.LabToggle
import dev.field.app.ui.components.PanelWidth
import dev.field.app.ui.components.SectionLabel
import dev.field.app.ui.components.SlotSize
import dev.field.app.ui.components.ToolbarHeight
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.AccentDark
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Border
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.Surface
import dev.field.app.ui.theme.SurfaceRaised
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.fields.DragRegion
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Vec2
import dev.field.core.sim.Boundary
import dev.field.core.sim.Integrator
import dev.field.core.viz.HeatmapMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SandboxScreen(
    state: EditorState,
    repo: SaveRepository,
    onExit: () -> Unit,
) {
    var showSavePanel by remember { mutableStateOf(false) }
    var showLaunchPanel by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var saveIndicator by remember { mutableStateOf<String?>(null) }
    var fps by remember { mutableStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            repo.export(state.toScene(), uri)
                .onSuccess { state.toast("已导出到所选位置") }
                .onFailure { state.toast("导出失败：${it.message}") }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            repo.import(uri)
                .onSuccess {
                    state.loadScene(it)
                    state.toast("已导入：${it.name}")
                }
                .onFailure { state.toast("导入失败：${it.message}") }
        }
    }

    LaunchedEffect(state.isRunning, state.settings.speed) {
        if (!state.isRunning) return@LaunchedEffect
        var previous = withFrameNanos { it }
        var accumulator = 0.0
        var frames = 0
        var periodStart = previous
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / 1.0e9).coerceAtMost(0.1)
            previous = now
            val fixed = state.settings.fixedDt
            if (elapsed > 0.0) {
                accumulator += elapsed * state.settings.speed
                var steps = 0
                while (accumulator >= fixed && steps < 4) {
                    state.engine.step(fixed)
                    accumulator -= fixed
                    steps++
                }
                if (accumulator > fixed) accumulator = 0.0
                state.tick++
                frames++
            }
            if (now - periodStart > 1_000_000_000L) {
                fps = frames
                frames = 0
                periodStart = now
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (state.dirty) {
                repo.saveAutosave(state.toScene())
                state.dirty = false
                saveIndicator = "自动保存"
            }
        }
    }

    LaunchedEffect(saveIndicator) {
        if (saveIndicator != null) {
            delay(2_500)
            saveIndicator = null
        }
    }

    LaunchedEffect(state.toastToken) {
        if (state.toastMessage != null) {
            delay(2_500)
            state.toastMessage = null
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        FieldCanvas(state, Modifier.fillMaxSize())

        TopHud(
            state = state,
            fps = fps,
            saveIndicator = saveIndicator,
            onExit = onExit,
            onSave = {
                repo.save(state.toScene())
                    .onSuccess {
                        state.dirty = false
                        saveIndicator = "已保存"
                    }
                    .onFailure { state.toast("保存失败：${it.message}") }
            },
            onOpenSavePanel = { showSavePanel = true },
            onOpenSettings = { showSettingsPanel = !showSettingsPanel },
            onOpenLaunch = { showLaunchPanel = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(8.dp)
                .fillMaxWidth(),
        )

        if (showSettingsPanel) {
            SettingsPanel(
                state = state,
                onClose = { showSettingsPanel = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .padding(top = 58.dp, bottom = 74.dp, end = 8.dp),
            )
        } else {
            state.selectedSource?.let { source ->
                InspectorPanel(
                    state = state,
                    source = source,
                    onClose = { state.select(null) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .statusBarsPadding()
                        .padding(top = 58.dp, bottom = 74.dp, end = 8.dp),
                )
            }
        }

        BottomToolbar(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(8.dp),
        )

        state.toastMessage?.let { message ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .background(Surface)
                    .border(1.dp, AccentDark)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(message, color = TextPrimary, fontSize = 12.sp)
            }
        }

        if (showSavePanel) {
            ModalPanel(onDismiss = { showSavePanel = false }) {
                SavePanelContent(
                    state = state,
                    onClose = { showSavePanel = false },
                    onSave = { name ->
                        val scene = state.toScene().copy(name = name)
                        repo.save(scene)
                            .onSuccess {
                                state.sceneName = name
                                state.dirty = false
                                saveIndicator = "已保存"
                                state.toast("已保存：$name")
                            }
                            .onFailure { state.toast("保存失败：${it.message}") }
                    },
                    onExport = { exportLauncher.launch(repo.safeName(state.sceneName) + ".field.json") },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                )
            }
        }

        if (showLaunchPanel) {
            ModalPanel(onDismiss = { showLaunchPanel = false }) {
                LaunchPanel(
                    state = state,
                    onClose = { showLaunchPanel = false },
                )
            }
        }
    }
}

@Composable
private fun TopHud(
    state: EditorState,
    fps: Int,
    saveIndicator: String?,
    onExit: () -> Unit,
    onSave: () -> Unit,
    onOpenSavePanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LabPanel(Modifier, accent = true) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabIconButton("≡", onExit)
                Column {
                    Text(state.sceneName, color = TextPrimary, fontSize = 13.sp)
                    Text(
                        "${state.sources.size} 个场源 · ${state.boundaryLabel()}",
                        color = TextSecondary,
                        fontSize = 9.sp,
                    )
                }
                Spacer(Modifier.width(4.dp))
                LabIconButton(
                    if (state.isRunning) "⏸" else "▶",
                    onClick = { state.isRunning = !state.isRunning },
                )
                LabIconButton("−", onClick = { state.speedDown() })
                Text(
                    "×${formatSpeed(state.settings.speed)}",
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp),
                )
                LabIconButton("+", onClick = { state.speedUp() })
                LabIconButton("↶", onClick = { state.undo() }, enabled = state.canUndo)
                LabIconButton("↷", onClick = { state.redo() }, enabled = state.canRedo)
            }
        }

        Spacer(Modifier.weight(1f))

        LabPanel(Modifier, accent = true) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProbeCounter(state)
                Text("FPS $fps", color = TextSecondary, fontSize = 10.sp)
                saveIndicator?.let {
                    Text(it, color = Accent, fontSize = 10.sp)
                }
                LabIconButton("＋", onOpenLaunch)
                LabIconButton("存", onSave)
                LabIconButton("档", onOpenSavePanel)
                LabIconButton("设", onOpenSettings, selected = false)
            }
        }
    }
}

@Composable
private fun ProbeCounter(state: EditorState) {
    @Suppress("UNUSED_VARIABLE")
    val tick = state.tick
    Text(
        "探针 ${state.engine.probes.size}",
        color = TextSecondary,
        fontSize = 10.sp,
    )
}

@Composable
private fun BottomToolbar(
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LabPanel(Modifier, accent = true) {
            Row(
                Modifier.padding(4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditorTool.entries.forEach { tool ->
                    ToolSlot(
                        state = state,
                        tool = tool,
                        selected = state.tool == tool,
                        onClick = {
                            state.tool = if (state.tool == tool) null else tool
                        },
                    )
                }
            }
        }

        if (state.tool == EditorTool.ELECTRIC) {
            LabPanel(Modifier, accent = true) {
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("极性", color = TextSecondary, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip("＋", state.chargeSign >= 0.0, Modifier.clickable { state.chargeSign = 1.0 })
                        Chip("－", state.chargeSign < 0.0, Modifier.clickable { state.chargeSign = -1.0 })
                    }
                }
            }
        }

        if (state.tool?.rotatable == true) {
            LabPanel(Modifier, accent = true) {
                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("旋转", color = TextSecondary, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabIconButton("↻", onClick = { state.rotate() })
                        Chip("${state.rotationDeg}°", true, Modifier.clickable { state.resetRotation() })
                    }
                }
            }
        }
        if (state.tool == null) {
            LabPanel(Modifier, accent = true) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("选择模式", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ToolSlot(
    state: EditorState,
    tool: EditorTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .size(SlotSize + 8.dp)
            .background(if (selected) Accent.copy(alpha = 0.22f) else SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, RectangleShape)
            .clickable(onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(tool.iconRes),
            contentDescription = tool.label,
            modifier = Modifier.size(26.dp),
        )
        Text(
            tool.label,
            color = if (selected) Accent else TextSecondary,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun InspectorPanel(
    state: EditorState,
    source: dev.field.core.fields.FieldSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabPanel(modifier.width(PanelWidth), accent = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel(sourceTitle(source))
            KeyValueRow("编号", "#${source.id}")
            KeyValueRow("位置", state.sources.indexOf(source).let { "第 ${it + 1} 个" })
            DividerLine()

            when (source) {
                is PointCharge -> {
                    LabSlider(
                        "电荷量", source.charge, -3.0..3.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as PointCharge).copy(charge = if (value == 0.0) 0.0001 else value)
                            }
                        },
                        valueText = "%.2f".format(source.charge),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LabButton("正电荷", { state.updateSelected { (it as PointCharge).copy(charge = 1.0) } })
                        LabButton("负电荷", { state.updateSelected { (it as PointCharge).copy(charge = -1.0) } })
                    }
                }

                is Magnet -> {
                    LabSlider(
                        "磁极强度", source.strength, 0.1..3.0,
                        onValueChange = { value ->
                            state.updateSelected { (it as Magnet).copy(strength = value) }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LabButton("旋转 45°", {
                            state.updateSelected {
                                (it as Magnet).copy(angle = it.angle + Math.PI / 4.0)
                            }
                        })
                        LabButton("N 朝右", {
                            state.updateSelected { (it as Magnet).copy(angle = 0.0) }
                        })
                    }
                }

                is MassSource -> {
                    LabSlider(
                        "质量", source.mass, 1.0..30.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as MassSource).copy(mass = roundTenth(value))
                            }
                        },
                        valueText = "%.1f".format(source.mass),
                    )
                }

                is GravityZone -> {
                    LabSlider(
                        "重力大小", source.gravity.length, 0.0..10.0,
                        onValueChange = { value ->
                            state.updateSelected {
                                val zone = it as GravityZone
                                zone.copy(gravity = zone.gravity.normalized().let { dir ->
                                    if (dir.lengthSquared == 0.0) Vec2(0.0, -1.0) * value else dir * value
                                })
                            }
                        },
                    )
                    AngleSlider(
                        value = EditorState.gravityAngleDegrees(source).toDouble(),
                        onValueChange = { degrees ->
                            state.updateSelected {
                                val zone = it as GravityZone
                                val magnitude = maxOf(zone.gravity.length, 0.01)
                                zone.copy(gravity = Vec2(1.0, 0.0).rotate(Math.toRadians(degrees)) * magnitude)
                            }
                        },
                    )
                    ZoneSizeSlider(state)
                }

                is ForceZone -> {
                    LabSlider(
                        "加速度大小", source.accel.length, 0.0..10.0,
                        onValueChange = { value ->
                            state.updateSelected {
                                val zone = it as ForceZone
                                val dir = zone.accel.normalized().let {
                                    if (it.lengthSquared == 0.0) Vec2(1.0, 0.0) else it
                                }
                                zone.copy(accel = dir * value)
                            }
                        },
                    )
                    AngleSlider(
                        value = EditorState.forceAngleDegrees(source).toDouble(),
                        onValueChange = { degrees ->
                            state.updateSelected {
                                val zone = it as ForceZone
                                zone.copy(accel = Vec2(1.0, 0.0).rotate(Math.toRadians(degrees)) * maxOf(zone.accel.length, 0.01))
                            }
                        },
                    )
                    ZoneSizeSlider(state)
                }

                is DragRegion -> {
                    LabSlider(
                        "线性阻力", source.linear, 0.0..3.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as DragRegion).copy(linear = value)
                            }
                        },
                    )
                    LabSlider(
                        "二次阻力", source.quadratic, 0.0..1.5,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as DragRegion).copy(quadratic = value)
                            }
                        },
                    )
                    LabSlider(
                        "风速 X", source.wind.x, -6.0..6.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as DragRegion).copy(wind = current.wind.copy(x = value))
                            }
                        },
                    )
                    LabSlider(
                        "风速 Y", source.wind.y, -6.0..6.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as DragRegion).copy(wind = current.wind.copy(y = value))
                            }
                        },
                    )
                    ZoneSizeSlider(state)
                }

                is WallSegment -> {
                    LabSlider(
                        "墙长", (source.end - source.start).length, 1.0..20.0,
                        onValueChange = { length ->
                            state.updateSelected {
                                val wall = it as WallSegment
                                val dir = (wall.end - wall.start).normalized().let {
                                    if (it.lengthSquared == 0.0) Vec2(1.0, 0.0) else it
                                }
                                val center = (wall.start + wall.end) * 0.5
                                wall.copy(start = center - dir * (length / 2.0), end = center + dir * (length / 2.0))
                            }
                        },
                    )
                    AngleSlider(
                        value = EditorState.wallAngleDegrees(source).toDouble(),
                        onValueChange = { degrees ->
                            state.updateSelected {
                                val wall = it as WallSegment
                                val length = (wall.end - wall.start).length.coerceAtLeast(1.0)
                                val center = (wall.start + wall.end) * 0.5
                                val dir = Vec2(1.0, 0.0).rotate(Math.toRadians(degrees))
                                wall.copy(start = center - dir * (length / 2.0), end = center + dir * (length / 2.0))
                            }
                        },
                    )
                    LabSlider(
                        "厚度", source.thickness, 0.1..1.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                (current as WallSegment).copy(thickness = value)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabButton("删除", {
                    state.removeSource(source.id)
                }, accent = false, danger = true, modifier = Modifier.weight(1f))
                LabButton("关闭", {
                    onClose()
                }, accent = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ZoneSizeSlider(state: EditorState) {
    val source = state.selectedSource ?: return
    val side = EditorState.zoneSide(source) ?: return
    LabSlider(
        "区域边长", side, 1.0..20.0,
        onValueChange = { value ->
            state.updateSelected { current ->
                val center = EditorState.zoneCenter(current) ?: return@updateSelected current
                val half = value / 2.0
                val min = Vec2(center.x - half, center.y - half)
                val max = Vec2(center.x + half, center.y + half)
                when (current) {
                    is GravityZone -> current.copy(min = min, max = max)
                    is ForceZone -> current.copy(min = min, max = max)
                    is DragRegion -> current.copy(min = min, max = max)
                    else -> current
                }
            }
        },
    )
}

@Composable
private fun AngleSlider(value: Double, onValueChange: (Double) -> Unit) {
    LabSlider(
        "方向角", value, 0.0..360.0,
        onValueChange = onValueChange,
        valueText = "${value.toInt()}°",
    )
}

@Composable
private fun SettingsPanel(
    state: EditorState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabPanel(modifier.width(PanelWidth), accent = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("世界设置")
            Text("边界", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Boundary.entries.forEach { boundary ->
                    val label = when (boundary) {
                        Boundary.REFLECT -> "反射"
                        Boundary.WRAP -> "环绕"
                        Boundary.OPEN -> "飞出"
                    }
                    Chip(label, state.boundary == boundary, Modifier.clickable {
                        state.updateWorld { it.copy(boundary = boundary) }
                    })
                }
            }

            DividerLine()
            SectionLabel("物理")
            Text("积分器", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("半隐式", state.settings.integrator == Integrator.SEMI_IMPLICIT, Modifier.clickable {
                    state.updateSettings { it.copy(integrator = Integrator.SEMI_IMPLICIT) }
                })
                Chip("RK4", state.settings.integrator == Integrator.RK4, Modifier.clickable {
                    state.updateSettings { it.copy(integrator = Integrator.RK4) }
                })
            }
            KeyValueRow("子步", state.settings.subSteps.toString())
            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabButton("−", { state.updateSettings { it.copy(subSteps = (it.subSteps - 1).coerceIn(1, 4)) } })
                LabButton("＋", { state.updateSettings { it.copy(subSteps = (it.subSteps + 1).coerceIn(1, 4)) } })
            }
            LabToggle("探针碰撞", state.settings.collisions, { value ->
                state.updateSettings { it.copy(collisions = value) }
            })
            Text("网格吸附", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.0 to "关", 0.5 to "0.5 m", 1.0 to "1 m").forEach { (step, label) ->
                    Chip(label, state.settings.gridSnap == step, Modifier.clickable {
                        state.updateSettings { it.copy(gridSnap = step) }
                    })
                }
            }

            DividerLine()
            SectionLabel("可视化")
            LabToggle("矢量箭头", state.visuals.arrows, { value ->
                state.updateVisuals { it.copy(arrows = value) }
            })
            LabToggle("场线", state.visuals.streamlines, { value ->
                state.updateVisuals { it.copy(streamlines = value) }
            })
            LabToggle("热图", state.visuals.heatmap, { value ->
                state.updateVisuals { it.copy(heatmap = value) }
            })
            LabToggle("探针拖尾", state.visuals.trails, { value ->
                state.updateVisuals { it.copy(trails = value) }
            })
            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("势能", state.visuals.heatmapMode == HeatmapMode.POTENTIAL, Modifier.clickable {
                    state.updateVisuals { it.copy(heatmapMode = HeatmapMode.POTENTIAL) }
                })
                Chip("场强", state.visuals.heatmapMode == HeatmapMode.MAGNITUDE, Modifier.clickable {
                    state.updateVisuals { it.copy(heatmapMode = HeatmapMode.MAGNITUDE) }
                })
            }

            DividerLine()
            SectionLabel("操作")
            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabButton("清空探针", { state.clearProbes() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                LabButton("关闭面板", onClose, accent = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SavePanelContent(
    state: EditorState,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var name by remember { mutableStateOf(state.sceneName) }
    Column(Modifier.width(320.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("存档内操作")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("世界名称") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceRaised,
                unfocusedContainerColor = SurfaceRaised,
                focusedIndicatorColor = Accent,
                unfocusedIndicatorColor = Border,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextSecondary,
            ),
        )
        LabButton("保存", { onSave(name) }, accent = true, modifier = Modifier.fillMaxWidth())
        LabButton("导出 JSON", onExport, modifier = Modifier.fillMaxWidth())
        LabButton("导入 JSON", onImport, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("清空探针", { state.clearProbes() }, modifier = Modifier.weight(1f))
            LabButton("关闭", onClose, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LaunchPanel(state: EditorState, onClose: () -> Unit) {
    var count by remember { mutableStateOf(20.0) }
    var speed by remember { mutableStateOf(8.0) }
    var charge by remember { mutableStateOf(1.0) }
    var spread by remember { mutableStateOf(20.0) }

    Column(Modifier.width(320.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("发射探针")
        LabSlider("数量", count, 1.0..200.0, { count = it.roundToInt().toDouble() }, valueText = count.roundToInt().toString())
        LabSlider("初速度", speed, 0.0..30.0, { speed = it }, valueText = "%.1f m/s".format(speed))
        LabSlider("电荷", charge, -3.0..3.0, { charge = it }, valueText = "%.2f".format(charge))
        LabSlider("散布角", spread, 0.0..180.0, { spread = it }, valueText = "${spread.roundToInt()}°")
        Text(
            "探针从世界中心向右发射，方向可在检查器中调整。",
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("发射", {
                state.launchProbes(
                    count = count.roundToInt(),
                    speed = speed,
                    charge = charge,
                    spreadDegrees = spread,
                )
                state.toast("已发射 ${count.roundToInt()} 个探针")
                onClose()
            }, accent = true, modifier = Modifier.weight(1f))
            LabButton("清空", { state.clearProbes() }, modifier = Modifier.weight(1f))
            LabButton("关闭", onClose, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModalPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clickable {}) {
            LabPanel(accent = true) {
                content()
            }
        }
    }
}

private fun EditorState.boundaryLabel(): String = when (boundary) {
    Boundary.REFLECT -> "反射边界"
    Boundary.WRAP -> "环绕边界"
    Boundary.OPEN -> "飞出边界"
}

private fun formatSpeed(speed: Double): String =
    if (speed < 1.0) speed.toString() else speed.toInt().toString()

private fun sourceTitle(source: dev.field.core.fields.FieldSource): String = when (source) {
    is PointCharge -> if (source.charge >= 0.0) "正电荷" else "负电荷"
    is Magnet -> "磁体（双磁极）"
    is MassSource -> "质量体"
    is GravityZone -> "重力区"
    is ForceZone -> "均匀力区"
    is DragRegion -> "阻力区"
    is WallSegment -> "墙段"
}

private fun roundTenth(value: Double): Double = Math.round(value * 10.0) / 10.0
