package dev.field.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.field.app.data.AppPrefs
import dev.field.app.data.Defaults
import dev.field.app.data.SaveRepository
import dev.field.app.editor.EditorState
import dev.field.app.ui.screens.AppSettingsScreen
import dev.field.app.ui.screens.HelpScreen
import dev.field.app.ui.screens.MenuScreen
import dev.field.app.ui.screens.SandboxScreen
import dev.field.app.ui.screens.SavesScreen
import dev.field.app.ui.transition.YellowWipeOverlay
import kotlinx.coroutines.launch

enum class AppScreen { MENU, SAVES, SETTINGS, HELP, SANDBOX }

@Composable
fun FieldLabApp() {
    val context = LocalContext.current
    val repo = remember { SaveRepository(context) }
    val prefs = remember { AppPrefs(context) }

    var screen by remember { mutableStateOf(AppScreen.MENU) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    val wipe = remember { Animatable(0f) }
    var transitioning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            wipe.animateTo(0.5f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
            screen = target
            wipe.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
            wipe.snapTo(0f)
            transitioning = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
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
        when (screen) {
            AppScreen.SANDBOX -> {
                editor?.let { state ->
                    repo.saveAutosave(state.toScene())
                    state.dirty = false
                }
                go(AppScreen.MENU)
            }

            else -> go(AppScreen.MENU)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.MENU -> MenuScreen(
                hasSession = editor != null,
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
                onBack = { go(AppScreen.MENU) },
                onOpen = { scene ->
                    editor = EditorState(scene)
                    go(AppScreen.SANDBOX)
                },
            )

            AppScreen.SETTINGS -> AppSettingsScreen(
                prefs = prefs,
                onBack = { go(AppScreen.MENU) },
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
