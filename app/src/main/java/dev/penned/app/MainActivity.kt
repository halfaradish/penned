package dev.penned.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.penned.app.ui.nav.PennedApp
import dev.penned.app.ui.theme.PennedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PennedTheme {
                PennedApp(application as PennedApplication)
            }
        }
    }
}
