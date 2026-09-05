package com.v2ray.ang.ui.settings

import androidx.compose.runtime.Composable
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.repository.SettingsRepository
import com.v2ray.ang.ui.base.BaseActivity
import com.v2ray.ang.ui.base.baseViewModels

class SettingsActivity : BaseActivity() {

    private val viewModel: SettingsViewModel by baseViewModels { _, _ ->
        SettingsViewModel(SettingsRepository())
    }

    private val platformEvents: (SettingsEvent) -> Boolean = ::handlePlatformEvent

    @Composable
    override fun ScreenContent() = SettingsScreen(viewModel, platformEvents)

    private fun handlePlatformEvent(event: SettingsEvent): Boolean = when (event) {
        is SettingsEvent.ApplyLanguage -> {
            AppLocaleManager.setApplicationLanguage(event.code)
            true
        }
    }
}
