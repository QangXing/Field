package dev.field.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.field.app.ui.FieldLabApp
import dev.field.app.ui.theme.FieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FieldTheme {
                FieldLabApp()
            }
        }
    }
}
