package com.v2ray.ang.ui.subscription

import androidx.compose.runtime.Composable
import com.v2ray.ang.repository.SubRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class SubEditActivity : BaseActivity() {

    private val viewModel: SubEditViewModel by baseViewModels { app, handle ->
        SubEditViewModel(handle, SubRepository(app))
    }

    @Composable
    override fun ScreenContent() = SubEditScreen(viewModel)
}
