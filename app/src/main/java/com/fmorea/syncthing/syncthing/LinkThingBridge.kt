package com.fmorea.syncthing.syncthing

import androidx.compose.ui.platform.ComposeView

object LinkThingBridge {
    @JvmStatic
    fun setContent(view: ComposeView, viewModel: LinkThingViewModel, scannedDeviceId: String = "") {
        view.setContent {
            LinkThingScreen(viewModel, scannedDeviceId)
        }
    }
}
