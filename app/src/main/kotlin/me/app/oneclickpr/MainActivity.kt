package me.app.oneclickpr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import me.app.oneclickpr.ui.screen.MainScreen
import me.app.oneclickpr.ui.theme.OneClickPRTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启用沉浸式边缘到边缘体验
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OneClickPRTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}