package com.v2ray.ang.ui.perappproxy

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.PerAppProxyRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class PerAppProxyActivity : BaseActivity() {

    private val viewModel: PerAppProxyViewModel by baseViewModels { app, handle ->
        PerAppProxyViewModel(PerAppProxyRepository(app), handle)
    }

    @Composable
    override fun ScreenContent() = PerAppProxyScreen(viewModel)
}
