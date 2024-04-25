package com.qvsorrow.demo.lowlevelvideo.core

import androidx.compose.runtime.saveable.Saver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds


private val durationSaver = Saver<Duration, Long>(
    save = { it.inWholeNanoseconds },
    restore = { it.nanoseconds },
)

val Duration.Companion.saver: Saver<Duration, Long> get() = durationSaver