package com.qvsorrow.demo.lowlevelvideo.ui.screen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.core.AUTHORITY
import com.qvsorrow.demo.lowlevelvideo.core.openVideoUri
import com.qvsorrow.demo.lowlevelvideo.core.saver
import com.qvsorrow.demo.lowlevelvideo.recorder.VideoRecorder
import com.qvsorrow.demo.lowlevelvideo.renderer.ColorAnimationRenderer
import com.qvsorrow.demo.lowlevelvideo.renderer.toSurfaceHolderCallback
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration


@Composable
fun VideoRecorderScreen(navController: NavController<Page>) {
    ScaffoldScreen(Strings.RECORDER_TITLE, onBack = { navController.navigateBack() }) {
        SceneRecording(navController)
    }

}

@Composable
private fun SceneRecording(navController: NavController<Page>) {
    val context = LocalContext.current
    val renderer = remember { ColorAnimationRenderer() }


    var rendererFrames by rememberSaveable { mutableIntStateOf(0) }
    var duration by rememberSaveable(stateSaver = Duration.saver) {
        mutableStateOf(Duration.ZERO)
    }

    var videoFile by rememberSaveable { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        if (videoFile != null) return@LaunchedEffect
        renderer.rendererFrames.collectLatest { rendererFrames = it }
    }

    LaunchedEffect(videoFile) {
        if (videoFile != null) return@LaunchedEffect
        renderer.duration.collectLatest { duration = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column {
                Text("Rendered $rendererFrames frames")
                Text("Total $duration")
                if (videoFile == null) {
                    Text(text = "Recording...", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column {
                videoFile?.let { uri ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Page.VideoPlayer(uri)) }) {
                        Text("View")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openVideoUri(context, uri, false) }) {
                        Text("Open")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openVideoUri(context, uri, true) }) {
                        Text("Share")
                    }
                }
            }
        }

    }


    LaunchedEffect(videoFile) {
        if (videoFile != null) return@LaunchedEffect
        val videoRecorder = VideoRecorder(context)
        val deferred = videoRecorder.record(720, 1280, renderer)
        val file = deferred.await()
        if (file != null) {
            val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            videoFile = uri
        }
    }

}








