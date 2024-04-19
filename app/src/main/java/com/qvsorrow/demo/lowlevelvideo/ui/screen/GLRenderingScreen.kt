package com.qvsorrow.demo.lowlevelvideo.ui.screen

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.renderer.ColorAnimationRenderer
import com.qvsorrow.demo.lowlevelvideo.renderer.toSurfaceHolderCallback
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings

@Composable
fun GLRenderingScreen(navController: NavController<Page>) {
    ScaffoldScreen(Strings.GL_RENDERING_TITLE, onBack = { navController.navigateBack() }) {

        val callback = remember {
            ColorAnimationRenderer().toSurfaceHolderCallback()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(callback)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }

}