package com.v2ray.ang.ui.about

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.AboutRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class AboutActivity : BaseActivity() {

    private val viewModel: AboutViewModel by baseViewModels { app, _ ->
        AboutViewModel(AboutRepository(app))
    }

    @Composable
    override fun ScreenContent() = AboutScreen(viewModel)
}
