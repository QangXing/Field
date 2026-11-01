package dev.field.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.field.app.ui.components.SectionLabel
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
import dev.field.core.fields.FieldSource
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Vec2
import dev.field.core.sim.Boundary
import dev.field.core.sim.BodyShape
import dev.field.core.sim.Integrator
import dev.field.core.viz.HeatmapMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
    var liveProbeCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val scene = state.toScene()
            scope.launch {
                val result = withContext(Dispatchers.IO) { repo.export(scene, uri) }
                result
                    .onSuccess { state.toast("已导出到所选位置") }
                    .onFailure { state.toast("导出失败：${it.message}") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { repo.import(uri) }
                result
                    .onSuccess {
                        val currentName = state.sceneName
                        state.loadScene(it)
                        if (it.name.isBlank()) state.sceneName = currentName
                        state.toast("已导入：${state.sceneName}")
                    }
                    .onFailure { state.toast("导入失败：${it.message}") }
            }
        }
    }

    LaunchedEffect(state.isRunning, state.settings.speed) {
        if (!state.isRunning) {
            state.renderAlpha = 1f
            liveProbeCount = state.engine.probes.size
            return@LaunchedEffect
        }
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
                    try {
                        state.engine.step(fixed)
                    } catch (error: Throwable) {
                        state.isRunning = false
                        state.toast("模拟已暂停：${error.message ?: "未知错误"}")
                        return@LaunchedEffect
                    }
                    accumulator -= fixed
                    steps++
                }
                if (accumulator > fixed) accumulator = 0.0
                state.renderAlpha = (accumulator / fixed).toFloat().coerceIn(0f, 1f)
                state.tick++
                frames++
            }
            if (now - periodStart > 1_000_000_000L) {
                fps = frames
                liveProbeCount = state.engine.probes.size
                frames = 0
                periodStart = now
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (state.dirty) {
                val scene = state.toScene()
                val result = withContext(Dispatchers.IO) { repo.saveAutosave(scene) }
                if (result.isSuccess) {
                    state.dirty = false
                    saveIndicator = "自动保存"
                }
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

    LaunchedEffect(state.selectedId, state.selectedBodyId) {
        if (state.selectedId != null || state.selectedBodyId != null) {
            showSettingsPanel = false
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        FieldCanvas(state, Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopHud(
                state = state,
                fps = fps,
                probeCount = liveProbeCount,
                saveIndicator = saveIndicator,
                onExit = onExit,
                onSave = {
                    val scene = state.toScene()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { repo.save(scene) }
                        result
                            .onSuccess {
                                state.dirty = false
                                saveIndicator = "已保存"
                            }
                            .onFailure { state.toast("保存失败：${it.message}") }
                    }
                },
                onOpenSavePanel = { showSavePanel = true },
                onOpenSettings = {
                    showSettingsPanel = !showSettingsPanel
                    if (showSettingsPanel) state.select(null)
                },
                onOpenLaunch = { showLaunchPanel = true },
            )

            state.toastMessage?.let { message ->
                Box(
                    Modifier
                        .background(Surface)
                        .border(1.dp, AccentDark)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(message, color = TextPrimary, fontSize = 11.sp)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showSettingsPanel) {
                SettingsSheet(
                    state = state,
                    onClose = { showSettingsPanel = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                )
            } else {
                val source = state.selectedSource
                val body = state.selectedBody
                if (source != null) {
                    SourceInspector(
                        state = state,
                        source = source,
                        onClose = { state.select(null) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    )
                } else if (body != null) {
                    BodyInspector(
                        state = state,
                        onClose = { state.selectBody(null) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    )
                }
            }

            ContextBar(state)
            BottomToolbar(state)
        }

        if (showSavePanel) {
            ModalPanel(onDismiss = { showSavePanel = false }) {
                SavePanelContent(
                    state = state,
                    onClose = { showSavePanel = false },
                    onSave = { name ->
                        val scene = state.toScene().copy(name = name)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { repo.save(scene) }
                            result
                                .onSuccess {
                                    state.sceneName = name
                                    state.dirty = false
                                    saveIndicator = "已保存"
                                    state.toast("已保存：$name")
                                }
                                .onFailure { state.toast("保存失败：${it.message}") }
                        }
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
private fun MiniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Int = 32,
) {
    Box(
        modifier
            .width(width.dp)
            .height(30.dp)
            .background(if (selected) Accent else SurfaceRaised, RectangleShape)
            .border(1.dp, if (selected) AccentDark else Border, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> TextSecondary.copy(alpha = 0.4f)
                selected -> Bg
                else -> TextPrimary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopHud(
    state: EditorState,
    fps: Int,
    probeCount: Int,
    saveIndicator: String?,
    onExit: () -> Unit,
    onSave: () -> Unit,
    onOpenSavePanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLaunch: () -> Unit,
) {
    val sourceCount = state.sources.size
    val bodyCount = state.bodies.size
    LabPanel(Modifier.fillMaxWidth(), accent = true) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton("≡", onExit, width = 34)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.sceneName,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "场 $sourceCount · 球/块 $bodyCount · 探针 $probeCount",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
                MiniButton(if (state.isRunning) "Ⅱ" else "▶", {
                    state.isRunning = !state.isRunning
                }, selected = !state.isRunning)
                MiniButton("−", { state.speedDown() })
                Text(
                    "×${formatSpeed(state.settings.speed)}",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                )
                MiniButton("+", { state.speedUp() })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniButton("↶", { state.undo() }, enabled = state.canUndo)
                MiniButton("↷", { state.redo() }, enabled = state.canRedo)
                MiniButton("⌫", { state.clearProbes() }, enabled = probeCount > 0)
                Spacer(Modifier.weight(1f))
                Text("FPS $fps", color = TextSecondary, fontSize = 9.sp)
                saveIndicator?.let {
                    Text(it, color = Accent, fontSize = 9.sp)
                }
                MiniButton("＋", onOpenLaunch)
                MiniButton("存", onSave)
                MiniButton("档", onOpenSavePanel)
                MiniButton("设", onOpenSettings, selected = false)
            }
        }
    }
}

@Composable
private fun BottomToolbar(state: EditorState) {
    LabPanel(Modifier.fillMaxWidth(), accent = true) {
        Row(
            Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolSlot(
                    label = "选择",
                    iconRes = EditorTool.SELECT.iconRes,
                    selected = state.tool == null,
                    onClick = {
                        state.tool = null
                        state.select(null)
                        state.selectBody(null)
                    },
                )
                EditorTool.placementSlots.filter { !it.isEraser }.forEach { tool ->
                    ToolSlot(
                        label = tool.label,
                        iconRes = tool.iconRes,
                        selected = state.tool == tool,
                        onClick = {
                            state.tool = if (state.tool == tool) null else tool
                            state.select(null)
                            state.selectBody(null)
                        },
                    )
                }
            }
            ToolSlot(
                label = EditorTool.ERASE.label,
                iconRes = EditorTool.ERASE.iconRes,
                selected = state.tool == EditorTool.ERASE,
                onClick = {
                    state.tool = if (state.tool == EditorTool.ERASE) null else EditorTool.ERASE
                    state.select(null)
                    state.selectBody(null)
                },
            )
        }
    }
}

@Composable
private fun ToolSlot(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .size(width = 48.dp, height = 52.dp)
            .background(if (selected) Accent.copy(alpha = 0.22f) else SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, RectangleShape)
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            color = if (selected) Accent else TextSecondary,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ContextBar(state: EditorState) {
    val tool = state.tool
    if (tool == null) {
        LabPanel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择模式", color = TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "拖动中心移动 · 拖动边框缩放",
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                )
            }
        }
        return
    }

    LabPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                tool.isEraser -> Text("橡皮：点击或拖过即删除", color = TextSecondary, fontSize = 10.sp)
                tool.isBody -> {
                    Text("放置 ${tool.label}", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        "质量 ${EditorState.format(state.bodyMass)} kg · 电荷 ${EditorState.format(state.bodyCharge, 2)} · ${EditorState.format(state.bodySize)} m",
                        color = TextPrimary,
                        fontSize = 9.sp,
                    )
                }

                tool == EditorTool.ELECTRIC -> {
                    Text("极性", color = TextSecondary, fontSize = 10.sp)
                    Chip("＋", state.chargeSign >= 0.0, Modifier.clickable { state.chargeSign = 1.0 })
                    Chip("－", state.chargeSign < 0.0, Modifier.clickable { state.chargeSign = -1.0 })
                }

                else -> Text("放置 ${tool.label}：点击落位 · 拖动连放", color = TextSecondary, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            if (tool.rotatable) {
                Chip("${state.rotationDeg}°", true, Modifier.clickable { state.resetRotation() })
                MiniButton("↻", { state.rotate() }, width = 30)
            }
        }
    }
}

@Composable
private fun SourceInspector(
    state: EditorState,
    source: FieldSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = state.sourceFrame(source)
    LabPanel(modifier, accent = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sourceTitle(source), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("#${source.id}", color = TextSecondary, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            MiniButton("✕", onClose, width = 28)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("场设置（拖动中心移动，拖动边框缩放）")
            LabSlider(
                "中心 X",
                frame.center.x,
                state.bounds.minX..state.bounds.maxX,
                onValueChange = { value ->
                    state.moveSourceTo(source.id, Vec2(state.snap(value), frame.center.y))
                },
                valueText = "${EditorState.format(frame.center.x)} m",
            )
            LabSlider(
                "中心 Y",
                frame.center.y,
                state.bounds.minY..state.bounds.maxY,
                onValueChange = { value ->
                    state.moveSourceTo(source.id, Vec2(frame.center.x, state.snap(value)))
                },
                valueText = "${EditorState.format(frame.center.y)} m",
            )
            if (source !is WallSegment) {
                if (frame.halfWidth == frame.halfHeight) {
                    LabSlider(
                        "边框大小",
                        frame.width,
                        0.5..20.0,
                        onValueChange = { value ->
                            state.resizeSource(source.id, value / 2.0, value / 2.0)
                        },
                        valueText = "${EditorState.format(frame.width)} m",
                    )
                } else {
                    LabSlider(
                        "边框宽",
                        frame.width,
                        1.0..20.0,
                        onValueChange = { value ->
                            state.resizeSource(source.id, value / 2.0, frame.halfHeight)
                        },
                        valueText = "${EditorState.format(frame.width)} m",
                    )
                    LabSlider(
                        "边框高",
                        frame.height,
                        1.0..20.0,
                        onValueChange = { value ->
                            state.resizeSource(source.id, frame.halfWidth, value / 2.0)
                        },
                        valueText = "${EditorState.format(frame.height)} m",
                    )
                }
            }
            DividerLine()

            when (source) {
                is PointCharge -> {
                    SectionLabel("匀强电场参数")
                    Text(
                        "边框内场强恒定，边框外为零。负值表示反向。",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    LabSlider(
                        "电场强度", abs(source.charge), 0.1..10.0,
                        onValueChange = { value ->
                            state.updateSelected { current ->
                                val charge = if (current is PointCharge) current.charge else 1.0
                                (current as PointCharge).copy(
                                    charge = if (charge < 0.0) -value else value,
                                )
                            }
                        },
                        valueText = "%.2f".format(abs(source.charge)),
                    )
                    AngleSlider(
                        value = EditorState.electricAngleDegrees(source).toDouble(),
                        onValueChange = { degrees ->
                            state.updateSelected {
                                (it as PointCharge).copy(angle = Math.toRadians(degrees))
                            }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LabButton("反向", {
                            state.updateSelected { (it as PointCharge).copy(charge = -it.charge) }
                        }, modifier = Modifier.weight(1f))
                        LabButton("朝右", {
                            state.updateSelected { (it as PointCharge).copy(angle = 0.0) }
                        }, modifier = Modifier.weight(1f))
                    }
                }

                is Magnet -> {
                    SectionLabel("匀强磁场参数")
                    Text(
                        "边框内 Bz 恒定。正值 ⊙ 穿出屏幕，负值 ⊗ 穿入屏幕。",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    LabSlider(
                        "磁感应强度 Bz", source.strength, -5.0..5.0,
                        onValueChange = { value ->
                            state.updateSelected {
                                (it as Magnet).copy(strength = if (value == 0.0) 0.0001 else value)
                            }
                        },
                        valueText = "%.2f".format(source.strength),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LabButton("反向", {
                            state.updateSelected { (it as Magnet).copy(strength = -it.strength) }
                        }, modifier = Modifier.weight(1f))
                        LabButton("⊙ +1.5", {
                            state.updateSelected { (it as Magnet).copy(strength = 1.5) }
                        }, modifier = Modifier.weight(1f))
                    }
                }

                is MassSource -> {
                    SectionLabel("引力参数")
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
                    SectionLabel("重力参数")
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
                                zone.copy(gravity = Vec2(1.0, 0.0).rotate(Math.toRadians(degrees)) * abs(zone.gravity.length))
                            }
                        },
                    )
                }

                is ForceZone -> {
                    SectionLabel("力场参数")
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
                                zone.copy(accel = Vec2(1.0, 0.0).rotate(Math.toRadians(degrees)) * abs(zone.accel.length))
                            }
                        },
                    )
                }

                is DragRegion -> {
                    SectionLabel("阻力参数")
                    LabSlider(
                        "线性阻力", source.linear, 0.0..3.0,
                        onValueChange = { value ->
                            state.updateSelected { (it as DragRegion).copy(linear = value) }
                        },
                    )
                    LabSlider(
                        "二次阻力", source.quadratic, 0.0..1.5,
                        onValueChange = { value ->
                            state.updateSelected { (it as DragRegion).copy(quadratic = value) }
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
                }

                is WallSegment -> {
                    SectionLabel("墙体参数")
                    LabSlider(
                        "墙长", (source.end - source.start).length, 1.0..20.0,
                        onValueChange = { length ->
                            state.updateSelected {
                                val wall = it as WallSegment
                                val dir = (wall.end - wall.start).normalized().let {
                                    if (it.lengthSquared == 0.0) Vec2(1.0, 0.0) else it
                                }
                                val center = (wall.start + wall.end) * 0.5
                                wall.copy(
                                    start = center - dir * (length / 2.0),
                                    end = center + dir * (length / 2.0),
                                )
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
                                wall.copy(
                                    start = center - dir * (length / 2.0),
                                    end = center + dir * (length / 2.0),
                                )
                            }
                        },
                    )
                    LabSlider(
                        "厚度", source.thickness, 0.1..1.0,
                        onValueChange = { value ->
                            state.updateSelected { (it as WallSegment).copy(thickness = value) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabButton("删除", { state.removeSource(source.id) }, danger = true, modifier = Modifier.weight(1f))
                LabButton("关闭", onClose, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BodyInspector(
    state: EditorState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = state.selectedBody ?: return
    LabPanel(modifier, accent = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (body.shape == BodyShape.CIRCLE) "运动小球" else "运动方块",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("#${body.id}", color = TextSecondary, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            MiniButton("✕", onClose, width = 28)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("物体参数（拖动中心位置移动）")
            KeyValueRow("重心", "(${EditorState.format(body.position.x)}, ${EditorState.format(body.position.y)}) m")
            KeyValueRow("速度", "${EditorState.format(body.velocity.length, 2)} m/s")
            DividerLine()
            LabSlider(
                "质量", body.mass, 0.1..20.0,
                onValueChange = { value -> state.updateSelectedBody { it.mass = roundTenth(value) } },
                valueText = "${EditorState.format(body.mass, 2)} kg",
            )
            LabSlider(
                "带电量", body.charge, -5.0..5.0,
                onValueChange = { value -> state.updateSelectedBody { it.charge = value } },
                valueText = "${EditorState.format(body.charge, 2)} C",
            )
            LabSlider(
                "尺寸", body.size, 0.1..3.0,
                onValueChange = { value -> state.updateSelectedBody { it.size = roundTenth(value) } },
                valueText = "${EditorState.format(body.size)} m",
            )
            LabSlider(
                "速度 X", body.velocity.x, -20.0..20.0,
                onValueChange = { value ->
                    state.updateSelectedBody { it.velocity = Vec2(value, it.velocity.y) }
                },
                valueText = "${EditorState.format(body.velocity.x, 2)} m/s",
            )
            LabSlider(
                "速度 Y", body.velocity.y, -20.0..20.0,
                onValueChange = { value ->
                    state.updateSelectedBody { it.velocity = Vec2(it.velocity.x, value) }
                },
                valueText = "${EditorState.format(body.velocity.y, 2)} m/s",
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabButton("静止", {
                    state.updateSelectedBody { it.velocity = Vec2.ZERO }
                }, modifier = Modifier.weight(1f))
                LabButton("删除", {
                    state.removeBody(body.id)
                }, danger = true, modifier = Modifier.weight(1f))
                LabButton("关闭", onClose, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
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
private fun SettingsSheet(
    state: EditorState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabPanel(modifier, accent = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("世界设置", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            MiniButton("✕", onClose, width = 28)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("边界与物理")
            Text("边界", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip("半隐式", state.settings.integrator == Integrator.SEMI_IMPLICIT, Modifier.clickable {
                    state.updateSettings { it.copy(integrator = Integrator.SEMI_IMPLICIT) }
                })
                Chip("RK4", state.settings.integrator == Integrator.RK4, Modifier.clickable {
                    state.updateSettings { it.copy(integrator = Integrator.RK4) }
                })
                Chip("子步 ${state.settings.subSteps}", true, Modifier.clickable {
                    state.updateSettings { it.copy(subSteps = if (it.subSteps >= 4) 1 else it.subSteps + 1) }
                })
                Chip("碰撞", state.settings.collisions, Modifier.clickable {
                    state.updateSettings { it.copy(collisions = !it.collisions) }
                })
            }
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("吸附", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                listOf(0.0 to "关", 0.5 to "0.5 m", 1.0 to "1 m").forEach { (step, label) ->
                    Chip(label, state.settings.gridSnap == step, Modifier.clickable {
                        state.updateSettings { it.copy(gridSnap = step) }
                    })
                }
            }

            DividerLine()
            SectionLabel("世界规则")
            RuleToggle("万有引力", state.rules.gravityEnabled) { value ->
                state.updateRules { it.copy(gravityEnabled = value) }
            }
            RuleToggle("带电体电荷传递", state.rules.chargeTransfer) { value ->
                state.updateRules { it.copy(chargeTransfer = value) }
            }
            RuleToggle("无视反弹", state.rules.ignoreBounce) { value ->
                state.updateRules { it.copy(ignoreBounce = value) }
            }
            RuleToggle("带电体漏电", state.rules.chargeLeak) { value ->
                state.updateRules { it.copy(chargeLeak = value) }
            }
            RuleToggle("粒子轨迹预测", state.rules.trajectoryPrediction) { value ->
                state.updateRules { it.copy(trajectoryPrediction = value) }
            }
            RuleToggle("粒子受力及力矩显示", state.rules.forceTorqueDisplay) { value ->
                state.updateRules { it.copy(forceTorqueDisplay = value) }
            }
            RuleToggle("带电体间库仑力", state.rules.chargeForceEnabled) { value ->
                state.updateRules { it.copy(chargeForceEnabled = value) }
            }
            RuleToggle("带电体受力显示", state.rules.chargeForceDisplay) { value ->
                state.updateRules { it.copy(chargeForceDisplay = value) }
            }
            RuleToggle("电流方向显示", state.rules.currentDirection) { value ->
                state.updateRules { it.copy(currentDirection = value) }
            }
            RuleToggle("粒子撞到物体后消散", state.rules.despawnOnImpact) { value ->
                state.updateRules { it.copy(despawnOnImpact = value) }
            }
            RuleToggle("粒子存活寿命", state.rules.particleLifetime) { value ->
                state.updateRules { it.copy(particleLifetime = value) }
            }
            RuleToggle("粒子运动轨迹线", state.rules.trails) { value ->
                state.updateRules { it.copy(trails = value) }
            }
            RuleToggle("场的箭头显示", state.rules.fieldArrows) { value ->
                state.updateRules { it.copy(fieldArrows = value) }
            }
            RuleToggle("重心显示", state.rules.centerOfGravity) { value ->
                state.updateRules { it.copy(centerOfGravity = value) }
            }

            DividerLine()
            SectionLabel("显示设置")
            LabToggle("隐藏默认案例", state.display.hideDefaults, onChange = { value ->
                state.updateDisplay { it.copy(hideDefaults = value) }
            })
            LabToggle("关闭滑动改变摄像机参数", state.display.lockCamera, onChange = { value ->
                state.updateDisplay { it.copy(lockCamera = value) }
            })

            DividerLine()
            SectionLabel("可视化")
            LabToggle("矢量箭头", state.visuals.arrows && state.rules.fieldArrows, onChange = { value ->
                state.updateVisuals { it.copy(arrows = value) }
                state.updateRules { it.copy(fieldArrows = value) }
            })
            LabToggle("场线", state.visuals.streamlines, onChange = { value ->
                state.updateVisuals { it.copy(streamlines = value) }
            })
            LabToggle("热图", state.visuals.heatmap, onChange = { value ->
                state.updateVisuals { it.copy(heatmap = value) }
            })
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("势能", state.visuals.heatmapMode == HeatmapMode.POTENTIAL, Modifier.clickable {
                    state.updateVisuals { it.copy(heatmapMode = HeatmapMode.POTENTIAL) }
                })
                Chip("场强", state.visuals.heatmapMode == HeatmapMode.MAGNITUDE, Modifier.clickable {
                    state.updateVisuals { it.copy(heatmapMode = HeatmapMode.MAGNITUDE) }
                })
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LabButton("清空探针", { state.clearProbes() }, modifier = Modifier.weight(1f))
                LabButton("清空物体", { state.clearBodies() }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RuleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabToggle(label, checked, onChange)
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
    Column(
        Modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("存档内操作")
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
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 12.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LabButton("保存", { onSave(name) }, accent = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("导出 JSON", onExport, modifier = Modifier.weight(1f))
            LabButton("导入 JSON", onImport, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("清空探针", { state.clearProbes() }, modifier = Modifier.weight(1f))
            LabButton("关闭", onClose, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LaunchPanel(state: EditorState, onClose: () -> Unit) {
    var probeCount by remember { mutableStateOf(12.0) }
    var probeSpeed by remember { mutableStateOf(8.0) }
    var probeCharge by remember { mutableStateOf(1.0) }
    var probeSpread by remember { mutableStateOf(20.0) }
    var bodyCount by remember { mutableStateOf(4.0) }
    var bodyMass by remember { mutableStateOf(1.5) }
    var bodyCharge by remember { mutableStateOf(0.0) }
    var bodySpeed by remember { mutableStateOf(6.0) }
    var bodySize by remember { mutableStateOf(0.6) }
    var bodySpread by remember { mutableStateOf(20.0) }
    var shape by remember { mutableStateOf(BodyShape.CIRCLE) }

    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        SectionLabel("发射探针")
        LabSlider("数量", probeCount, 1.0..200.0, { probeCount = it.roundToInt().toDouble() }, valueText = probeCount.roundToInt().toString())
        LabSlider("初速度", probeSpeed, 0.0..30.0, { probeSpeed = it }, valueText = "%.1f m/s".format(probeSpeed))
        LabSlider("电荷", probeCharge, -3.0..3.0, { probeCharge = it }, valueText = "%.2f".format(probeCharge))
        LabSlider("散布角", probeSpread, 0.0..180.0, { probeSpread = it }, valueText = "${probeSpread.roundToInt()}°")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabButton("发射", {
                state.launchProbes(probeCount.roundToInt(), probeSpeed, probeCharge, probeSpread)
                state.toast("已发射 ${probeCount.roundToInt()} 个探针")
            }, accent = true, modifier = Modifier.weight(1f))
            LabButton("清空", { state.clearProbes() }, modifier = Modifier.weight(1f))
        }

        DividerLine()
        SectionLabel("发射运动物体")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Chip("小球", shape == BodyShape.CIRCLE, Modifier.clickable { shape = BodyShape.CIRCLE })
            Chip("方块", shape == BodyShape.BOX, Modifier.clickable { shape = BodyShape.BOX })
            Spacer(Modifier.weight(1f))
            Text("从世界中心向右发射", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
        LabSlider("数量", bodyCount, 1.0..40.0, { bodyCount = it.roundToInt().toDouble() }, valueText = bodyCount.roundToInt().toString())
        LabSlider("质量", bodyMass, 0.1..20.0, { bodyMass = it }, valueText = "%.2f kg".format(bodyMass))
        LabSlider("带电量", bodyCharge, -5.0..5.0, { bodyCharge = it }, valueText = "%.2f C".format(bodyCharge))
        LabSlider("初速度", bodySpeed, 0.0..30.0, { bodySpeed = it }, valueText = "%.1f m/s".format(bodySpeed))
        LabSlider("尺寸", bodySize, 0.1..3.0, { bodySize = it }, valueText = "%.1f m".format(bodySize))
        LabSlider("散布角", bodySpread, 0.0..180.0, { bodySpread = it }, valueText = "${bodySpread.roundToInt()}°")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabButton("发射物体", {
                state.launchBodies(
                    count = bodyCount.roundToInt(),
                    shape = shape,
                    mass = bodyMass,
                    charge = bodyCharge,
                    speed = bodySpeed,
                    size = bodySize,
                    spreadDegrees = bodySpread,
                )
                state.toast("已发射 ${bodyCount.roundToInt()} 个物体")
            }, accent = true, modifier = Modifier.weight(1f))
            LabButton("清空物体", { state.clearBodies() }, modifier = Modifier.weight(1f))
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
        Box(Modifier.clickable { }) {
            LabPanel(accent = true, modifier = Modifier.padding(12.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
    }
}

private fun formatSpeed(speed: Double): String =
    if (speed < 1.0) speed.toString() else speed.toInt().toString()

private fun sourceTitle(source: FieldSource): String = when (source) {
    is PointCharge -> "匀强电场"
    is Magnet -> "匀强磁场"
    is MassSource -> "质量体"
    is GravityZone -> "重力区"
    is ForceZone -> "均匀力区"
    is DragRegion -> "阻力区"
    is WallSegment -> "墙段"
}

private fun roundTenth(value: Double): Double = Math.round(value * 10.0) / 10.0
