package com.qvsorrow.demo.lowlevelvideo.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings


@Composable
fun HomeScreen(navController: NavController<Page>) {
    ScaffoldScreen(Strings.HOME_TITLE) {
        Column(modifier = Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
            Button(onClick = { navController.navigate(Page.VideoPlayer()) }) {
                Text(Strings.PLAYER_TITLE)
            }
            Button(onClick = { navController.navigate(Page.VideoRecorder) }) {
                Text(Strings.RECORDER_TITLE)
            }
            Button(onClick = { navController.navigate(Page.VideoTranscoder) }) {
                Text(Strings.TRANSCODER_TITLE)
            }
            Button(onClick = { navController.navigate(Page.GLRendering) }) {
                Text(Strings.GL_RENDERING_TITLE)
            }
        }
    }
}


