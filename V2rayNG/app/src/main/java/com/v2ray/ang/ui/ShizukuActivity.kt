package com.v2ray.ang.ui

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity

class ShizukuActivity : BaseComponentActivity() {
    private val viewModel: ShizukuViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TetheringScreen(
            state = state,
            onBackClick = { finish() },
            onAction = viewModel::onAction,
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
