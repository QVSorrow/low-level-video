package com.qvsorrow.demo.lowlevelvideo.ui.screen

import androidx.compose.runtime.Composable
import com.qvsorrow.demo.lowlevelvideo.Page
import com.qvsorrow.demo.lowlevelvideo.ui.components.ScaffoldScreen
import com.qvsorrow.demo.lowlevelvideo.ui.navigation.NavController
import com.qvsorrow.demo.lowlevelvideo.ui.resources.Strings

@Composable
fun VideoTranscodingScreen(navController: NavController<Page>) {
    ScaffoldScreen(Strings.TRANSCODER_TITLE, onBack = { navController.navigateBack() }) {

    }
}