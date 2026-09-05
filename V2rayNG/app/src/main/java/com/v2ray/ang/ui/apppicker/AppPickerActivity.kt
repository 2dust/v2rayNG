package com.v2ray.ang.ui.apppicker

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.AppListRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class AppPickerActivity : BaseActivity() {

    private val viewModel: AppPickerViewModel by baseViewModels { app, handle ->
        AppPickerViewModel(AppListRepository(app), handle)
    }

    @Composable
    override fun ScreenContent() = AppPickerScreen(viewModel)
}
