package com.qvsorrow.demo.lowlevelvideo.core

import android.media.MediaFormat

data class TrackInfo(
    val id: Int,
    val format: MediaFormat,
) {

    val mime: String = format.getString(MediaFormat.KEY_MIME).orEmpty()

    fun mimeStartsWith(mime: String): Boolean {
        return format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mime)
    }

}