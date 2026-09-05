package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.ServerRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class ServerEditActivity : BaseActivity() {

    private val viewModel: ServerEditViewModel by baseViewModels { app, handle ->
        ServerEditViewModel(handle, ServerRepository(app))
    }

    @Composable
    override fun ScreenContent() = ServerEditScreen(viewModel)
}
