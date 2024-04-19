package com.qvsorrow.demo.lowlevelvideo

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface Page : Parcelable {
    @Parcelize
    data object Home : Page

    @Parcelize
    data class VideoPlayer(val video: Uri? = null) : Page

    @Parcelize
    data object VideoRecorder : Page

    @Parcelize
    data object VideoTranscoder : Page
    @Parcelize
    data object GLRendering : Page
}