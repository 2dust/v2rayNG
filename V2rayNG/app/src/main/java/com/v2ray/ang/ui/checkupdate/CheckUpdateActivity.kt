package com.v2ray.ang.ui.checkupdate

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.CheckUpdateRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class CheckUpdateActivity : BaseActivity() {

    private val viewModel: CheckUpdateViewModel by baseViewModels { _, _ ->
        CheckUpdateViewModel(CheckUpdateRepository())
    }

    @Composable
    override fun ScreenContent() = CheckUpdateScreen(viewModel)
}
