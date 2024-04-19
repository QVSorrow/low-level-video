package com.qvsorrow.demo.lowlevelvideo.renderer

import android.view.Surface
import androidx.annotation.CallSuper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

abstract class SurfaceRenderer {

    private val _duration = MutableStateFlow(Duration.ZERO)
    private val _rendererFrames = MutableStateFlow(0)

    val duration: StateFlow<Duration> = _duration.asStateFlow()
    val rendererFrames: StateFlow<Int> = _rendererFrames.asStateFlow()

    /**
     * @param surface   a [Surface] to draw on
     * @param frame     current frame index
     * @param time      current frame time
     *
     * @return `true` if it was the last frame
     */
    @CallSuper
    open fun onRenderFrame(surface: Surface, frame: Int, time: Duration): Boolean {
        _rendererFrames.value = frame + 1
        _duration.value = time
        return true
    }

    open fun onSizeChanged(surface: Surface, width: Int, height: Int) = Unit

    open fun onPrepare(surface: Surface) = Unit

    open fun onRelease() = Unit
}


