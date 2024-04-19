package com.qvsorrow.demo.lowlevelvideo.player

import android.view.Surface
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.StateFlow

interface Player {
    val timeUs: StateFlow<Long>
    val totalTimeUs: StateFlow<Long>
    val dimensions: StateFlow<IntSize>
    val isPlaying: StateFlow<Boolean>
    fun prepare(surface: Surface)
    fun play()
    fun pause()
    fun seekTo(positionUs: Long)
    fun release()
}