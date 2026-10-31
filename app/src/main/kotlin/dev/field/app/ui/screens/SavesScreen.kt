package dev.field.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.data.SaveRepository
import dev.field.app.ui.components.LabButton
import dev.field.app.ui.components.LabIconButton
import dev.field.app.ui.components.LabPanel
import dev.field.app.ui.components.SectionLabel
import dev.field.app.ui.components.TitleText
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary
import dev.field.core.scene.Scene
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavesScreen(
    repo: SaveRepository,
    onBack: () -> Unit,
    onOpen: (Scene) -> Unit,
) {
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

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
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabIconButton("←", onBack)
            TitleText("存档")
            Spacer(Modifier.weight(1f))
            LabButton("导入 JSON", { importLauncher.launch(arrayOf("*/*")) })
        }

        message?.let {
            Text(
                it,
                color = Accent,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (autosave != null) {
                item {
                    LabPanel(Modifier.fillMaxWidth(), accent = true) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("自动存档", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    autosave.name,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                            LabButton("载入", { onOpen(autosave) }, accent = true)
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "还没有存档。在世界内点击「档」即可保存。",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }

            items(entries, key = { it.file.absolutePath }) { entry ->
                LabPanel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = TextPrimary, fontSize = 13.sp)
                            Text(
                                formatTime(entry.modified),
                                color = TextSecondary,
                                fontSize = 10.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabButton("载入", {
                                repo.load(entry.file)
                                    .onSuccess { onOpen(it) }
                                    .onFailure { message = "载入失败：${it.message}" }
                            }, accent = true)
                            LabButton("删除", {
                                repo.delete(entry.file)
                                message = "已删除：${entry.name}"
                                refresh++
                            }, danger = true)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
