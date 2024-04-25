package com.qvsorrow.demo.lowlevelvideo.ui.screen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.R
import com.qvsorrow.demo.lowlevelvideo.core.AUTHORITY
import com.qvsorrow.demo.lowlevelvideo.core.openVideoUri
import com.qvsorrow.demo.lowlevelvideo.core.saver
import com.qvsorrow.demo.lowlevelvideo.transcoder.OutputConfiguration
import com.qvsorrow.demo.lowlevelvideo.transcoder.TranscoderProgressListener
import com.qvsorrow.demo.lowlevelvideo.transcoder.VideoTranscoder
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds


private fun uri(context: Context) = Uri.parse(
    buildString {
        append("android.resource://")
            .append(context.applicationContext.packageName)
            .append("/")
            .append(R.raw.sample)
    }
)

@Composable
fun VideoTranscodingScreen(navController: NavController<Page>) {
    ScaffoldScreen(Strings.TRANSCODER_TITLE, onBack = { navController.navigateBack() }) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var text by remember { mutableStateOf("") }

        var extractTime by rememberSaveable(stateSaver = Duration.saver) { mutableStateOf(Duration.ZERO) }
        var decoderTime by rememberSaveable(stateSaver = Duration.saver) { mutableStateOf(Duration.ZERO) }
        var muxerTime by rememberSaveable(stateSaver = Duration.saver) { mutableStateOf(Duration.ZERO) }
        var uri by rememberSaveable { mutableStateOf<Uri?>(null) }

        val listener = remember {
            object : TranscoderProgressListener {
                override fun extractTime(time: Long) {
                    scope.launch {
                        if (extractTime.inWholeMicroseconds < time) {
                            extractTime = time.microseconds
                        }
                    }
                }

                override fun decoderTime(time: Long) {
                    scope.launch {
                        if (decoderTime.inWholeMicroseconds < time) {
                            decoderTime = time.microseconds
                        }
                    }
                }

                override fun muxerTime(time: Long) {
                    scope.launch {
                        if (muxerTime.inWholeMicroseconds < time) {
                            muxerTime = time.microseconds
                        }
                    }

                }
            }
        }

        LaunchedEffect(uri) {
            if (uri != null) return@LaunchedEffect
            val transcoder = VideoTranscoder(context)
            withContext(Dispatchers.IO + NonCancellable) {
                text = "Transcoding..."
                val configuration = OutputConfiguration(
                    bitrate = 1_000_000,
                )
                val file = transcoder.execute(uri(context), configuration, listener)
                text = "Complete!"
                uri = FileProvider.getUriForFile(context, AUTHORITY, file)
            }
        }

        Column(
            Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(text = text)
            Text(text = "extractTime:   $extractTime")
            Text(text = "decoderTime:   $decoderTime")
            Text(text = "muxerTime:     $muxerTime")

            uri?.let {
                TextButton(onClick = { navController.navigate(Page.VideoPlayer(it)) }) {
                    Text(text = "View")
                }
                TextButton(onClick = { openVideoUri(context, it, isSend = false) }) {
                    Text(text = "Open in")
                }
                TextButton(onClick = { openVideoUri(context, it, isSend = true) }) {
                    Text(text = "Share")
                }
            }
        }
    }
}