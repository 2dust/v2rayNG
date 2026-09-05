package com.v2ray.ang.ui.routing

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.RoutingRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class RoutingEditActivity : BaseActivity() {

    private val viewModel: RoutingEditViewModel by baseViewModels { app, handle ->
        RoutingEditViewModel(handle, RoutingRepository(app))
    }

    @Composable
    override fun ScreenContent() = RoutingEditScreen(viewModel)
}
