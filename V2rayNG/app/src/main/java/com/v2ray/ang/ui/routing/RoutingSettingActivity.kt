package com.v2ray.ang.ui.routing

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.RoutingRepository
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels

class RoutingSettingActivity : BaseHelperActivity() {

    private val viewModel: RoutingSettingViewModel by baseViewModels { app, _ ->
        RoutingSettingViewModel(RoutingRepository(app))
    }

    private val platformEventHandler: (RoutingEvent) -> Boolean = { event ->
        when (event) {
            RoutingEvent.ScanQrCode -> {
                scanQrCode { text -> viewModel.onAction(RoutingAction.QrCodeScanned(text)) }
                true
            }
            else -> false
        }
    }

    @Composable
    override fun ScreenContent() = RoutingSettingScreen(viewModel, platformEventHandler)
}
