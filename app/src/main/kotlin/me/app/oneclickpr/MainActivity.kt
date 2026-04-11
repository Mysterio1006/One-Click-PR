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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 从分享菜单或浏览器深度链接传入的 GitHub 仓库地址
        val deepLinkUrl = intent?.data?.toString()

        setContent {
            OneClickPRTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(deepLinkUrl = deepLinkUrl)
                }
            }
        }
    }
}
