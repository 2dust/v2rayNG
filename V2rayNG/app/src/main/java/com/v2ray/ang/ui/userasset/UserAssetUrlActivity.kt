package com.v2ray.ang.ui.userasset

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.UserAssetRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class UserAssetUrlActivity : BaseActivity() {

    private val viewModel: UserAssetUrlViewModel by baseViewModels { app, handle ->
        UserAssetUrlViewModel(handle, UserAssetRepository(app))
    }

    @Composable
    override fun ScreenContent() = UserAssetUrlScreen(viewModel)
}
