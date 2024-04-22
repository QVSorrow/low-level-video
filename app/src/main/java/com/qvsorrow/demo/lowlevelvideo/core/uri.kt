package com.qvsorrow.demo.lowlevelvideo.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

fun openVideoUri(context: Context, uri: Uri, isSend: Boolean) {
    val viewIntent = Intent().apply {
        type = "video/mp4"
        action = if (isSend) Intent.ACTION_SEND else Intent.ACTION_VIEW
        putExtra(Intent.EXTRA_STREAM, uri)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        clipData = ClipData.newRawUri("Video", uri)
    }
    val chooserIntent = Intent.createChooser(viewIntent, "Open video")
    context.startActivity(chooserIntent)
}