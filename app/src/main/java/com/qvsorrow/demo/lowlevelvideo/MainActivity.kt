package com.qvsorrow.demo.lowlevelvideo

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.Navigation
import com.qvsorrow.demo.lowlevelvideo.ui.screen.GLRenderingScreen
import com.qvsorrow.demo.lowlevelvideo.ui.screen.HomeScreen
import com.qvsorrow.demo.lowlevelvideo.ui.screen.VideoPlayerScreen
import com.qvsorrow.demo.lowlevelvideo.ui.screen.VideoRecorderScreen
import com.qvsorrow.demo.lowlevelvideo.ui.screen.VideoTranscodingScreen
import com.qvsorrow.demo.lowlevelvideo.ui.theme.LowLevelVideoTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LowLevelVideoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Navigation<Page>(Page.Home) {
                        page<Page.Home> {
                            HomeScreen(navController)
                        }
                        page<Page.VideoPlayer> {
                            VideoPlayerScreen(navController, page.video)
                        }
                        page<Page.VideoRecorder> {
                            VideoRecorderScreen(navController)
                        }
                        page<Page.VideoTranscoder> {
                            VideoTranscodingScreen(navController)
                        }
                        page<Page.GLRendering> {
                            GLRenderingScreen(navController)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CreateVideoDemo() {

}

