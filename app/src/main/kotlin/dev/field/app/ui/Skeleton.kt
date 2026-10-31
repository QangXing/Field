package dev.field.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.TextSecondary

@Composable
fun SkeletonScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FIELD LAB", color = Accent)
        Text("bootstrapping", color = TextSecondary)
    }
}
