package com.v2ray.ang.ui.userasset

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.UserAssetRepository
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels

class UserAssetActivity : BaseHelperActivity() {

    private val viewModel: UserAssetViewModel by baseViewModels { app, _ ->
        UserAssetViewModel(UserAssetRepository(app))
    }

    @Composable
    override fun ScreenContent() = UserAssetScreen(viewModel)
}
