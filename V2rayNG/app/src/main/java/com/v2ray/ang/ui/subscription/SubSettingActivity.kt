package com.v2ray.ang.ui.subscription

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.SubRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class SubSettingActivity : BaseActivity() {

    private val viewModel: SubSettingViewModel by baseViewModels { app, _ ->
        SubSettingViewModel(SubRepository(app))
    }

    @Composable
    override fun ScreenContent() = SubSettingScreen(viewModel)
}
