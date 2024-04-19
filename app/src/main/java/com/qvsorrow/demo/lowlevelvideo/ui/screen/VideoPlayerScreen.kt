package com.qvsorrow.demo.lowlevelvideo.ui.screen

import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.R
import com.qvsorrow.demo.lowlevelvideo.player.PlaybackAndSeekPlayer
import com.qvsorrow.demo.lowlevelvideo.player.Player
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun VideoPlayerScreen(navController: NavController<Page>, videoUri: Uri?) {
    ScaffoldScreen(Strings.PLAYER_TITLE, onBack = { navController.navigateBack() }) {
        val localContext = LocalContext.current
        Player(uri = videoUri ?: Uri.parse(
            buildString {
                append("android.resource://")
                    .append(localContext.applicationContext.packageName)
                    .append("/")
                    .append(R.raw.reel_demo)
            }
        ), modifier = Modifier.wrapContentSize())
    }
}


@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun Player(uri: Uri, modifier: Modifier = Modifier) {
    val localContext = LocalContext.current

    val player: Player = remember {
        PlaybackAndSeekPlayer(
            localContext,
            uri,
        )
    }
    val dimensions by player.dimensions.collectAsState()

    val listener = remember(player) { PlayerHolderCallback(player) }

    Box(modifier) {
        val totalTime by player.totalTimeUs.collectAsState()
        val playbackTime by player.timeUs.collectAsState()
        val playerProgress by remember {
            derivedStateOf { (playbackTime.toDouble() / totalTime).toFloat() }
        }
        val isPlaying by player.isPlaying.collectAsState()
        var progress by remember { mutableFloatStateOf(playerProgress) }
        var showPlayerProgress by remember { mutableStateOf(true) }
        val seekChannel = remember { Channel<Long>(Channel.CONFLATED) }
        val showPlayerProgressChannel = remember { Channel<Unit>(Channel.CONFLATED) }

        LaunchedEffect(Unit) {
            snapshotFlow { playerProgress }
                .collectLatest {
                    if (showPlayerProgress) {
                        progress = it
                    }
                }
        }

        LaunchedEffect(Unit) {
            seekChannel.receiveAsFlow()
                .collectLatest { player.seekTo(it) }
        }

        LaunchedEffect(Unit) {
            showPlayerProgressChannel.receiveAsFlow()
                .debounce(500.milliseconds)
                .collectLatest { showPlayerProgress = true }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(listener)
                        }
                    },
                    modifier = Modifier
                        .aspectRatio(
                            ratio = dimensions.width.toFloat() / dimensions.height,
                            matchHeightConstraintsFirst = dimensions.height > dimensions.width
                        )
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }
                )

                var iconAlpha by remember { mutableFloatStateOf(1f) }

                LaunchedEffect(isPlaying) {
                    animate(
                        1f,
                        0f,
                        animationSpec = tween(durationMillis = 1000, delayMillis = 1000)
                    ) { value, _ ->
                        iconAlpha = value
                    }
                }

                Icon(
                    if (isPlaying) {
                        painterResource(R.drawable.play_circle)
                    } else {
                        painterResource(R.drawable.pause_circle)
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.Center)
                        .alpha(iconAlpha),
                )
            }

            Slider(
                value = progress,
                onValueChange = { position ->
                    showPlayerProgress = false
                    progress = position
                    seekChannel.trySend((totalTime * position).roundToLong())
                },
                onValueChangeFinished = {
                    showPlayerProgressChannel.trySend(Unit)
                },
                thumb = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}


private class PlayerHolderCallback(private val player: Player) : SurfaceHolder.Callback {
    override fun surfaceCreated(holder: SurfaceHolder) {
        player.prepare(holder.surface)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        player.release()
    }
}
