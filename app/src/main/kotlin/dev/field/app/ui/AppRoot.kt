package dev.field.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.field.app.data.AppPrefs
import dev.field.app.data.Defaults
import dev.field.app.data.SaveRepository
import dev.field.app.editor.EditorState
import dev.field.app.ui.screens.AppSettingsScreen
import dev.field.app.ui.screens.HelpScreen
import dev.field.app.ui.screens.MenuBackgroundScreen
import dev.field.app.ui.screens.MenuScreen
import dev.field.app.ui.screens.SandboxScreen
import dev.field.app.ui.screens.SavesScreen
import dev.field.app.ui.transition.YellowWipeOverlay
import kotlinx.coroutines.launch

enum class AppScreen { MENU, SAVES, SETTINGS, HELP, BACKGROUND, SANDBOX }

@Composable
fun FieldLabApp() {
    val context = LocalContext.current
    val repo = remember { SaveRepository(context) }
    val prefs = remember { AppPrefs(context) }

    var screen by remember { mutableStateOf(AppScreen.MENU) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var backgroundRefresh by remember { mutableStateOf(0) }
    val wipe = remember { Animatable(0f) }
    var transitioning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val backgroundScene = remember(prefs.menuBackgroundSource, backgroundRefresh) {
        when (val source = prefs.menuBackgroundSource) {
            "none" -> null
            "demo" -> Defaults.demoScene()
            else -> repo.loadNamed(source)
        }
    }

    fun go(target: AppScreen) {
        if (screen == target && screen != AppScreen.SANDBOX) return
        if (transitioning) return
        if (prefs.reducedMotion) {
            screen = target
            return
        }
        transitioning = true
        scope.launch {
            wipe.snapTo(0f)
            var swapped = false
            wipe.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            ) {
                if (!swapped && value >= 0.5f) {
                    swapped = true
                    screen = target
                }
            }
            if (!swapped) screen = target
            wipe.snapTo(0f)
            transitioning = false
        }
    }

    LaunchedEffect(Unit) {
        if (editor == null) {
            repo.loadAutosave()?.let { editor = EditorState(it) }
        }
    }

    fun leaveSandbox() {
        editor?.let { state ->
            repo.saveAutosave(state.toScene())
            state.dirty = false
        }
        go(AppScreen.MENU)
    }

    BackHandler(enabled = screen != AppScreen.MENU) {
        if (screen == AppScreen.SANDBOX) {
            leaveSandbox()
        } else {
            go(AppScreen.MENU)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.MENU -> MenuScreen(
                hasSession = editor != null,
                background = backgroundScene,
                backgroundCenter = dev.field.core.math.Vec2(prefs.menuBackgroundX, prefs.menuBackgroundY),
                backgroundZoom = prefs.menuBackgroundZoom,
                onContinue = { if (editor != null) go(AppScreen.SANDBOX) },
                onNewWorld = {
                    editor = EditorState(Defaults.emptyScene())
                    go(AppScreen.SANDBOX)
                },
                onDemo = {
                    editor = EditorState(Defaults.demoScene())
                    go(AppScreen.SANDBOX)
                },
                onSaves = { go(AppScreen.SAVES) },
                onSettings = { go(AppScreen.SETTINGS) },
                onHelp = { go(AppScreen.HELP) },
            )

            AppScreen.SAVES -> SavesScreen(
                repo = repo,
                prefs = prefs,
                onBack = { go(AppScreen.MENU) },
                onOpen = { scene ->
                    editor = EditorState(scene)
                    go(AppScreen.SANDBOX)
                },
            )

            AppScreen.SETTINGS -> AppSettingsScreen(
                prefs = prefs,
                onOpenBackground = { go(AppScreen.BACKGROUND) },
                onBack = { go(AppScreen.MENU) },
            )

            AppScreen.BACKGROUND -> MenuBackgroundScreen(
                repo = repo,
                prefs = prefs,
                onBack = { go(AppScreen.SETTINGS) },
                onApplied = { backgroundRefresh++ },
            )

            AppScreen.HELP -> HelpScreen(onBack = { go(AppScreen.MENU) })

            AppScreen.SANDBOX -> {
                val state = editor
                if (state != null) {
                    key(state) {
                        SandboxScreen(
                            state = state,
                            repo = repo,
                            onExit = { leaveSandbox() },
                        )
                    }
                }
            }
        }

        if (transitioning) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var pressed = true
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                pressed = event.changes.any { it.pressed }
                            } while (pressed)
                        }
                    },
            ) {
                YellowWipeOverlay(wipe.value)
            }
        }
    }
}
