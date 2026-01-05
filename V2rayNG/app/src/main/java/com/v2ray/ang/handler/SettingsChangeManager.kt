package com.v2ray.ang.handler

import kotlinx.coroutines.flow.MutableStateFlow

object SettingsChangeManager {
    private val _restartService = MutableStateFlow(false)
    private val _reinitGroupTab = MutableStateFlow(false)

    // Mark restartService as requiring a restart
    fun makeRestartService() {
        _restartService.value = true
    }

    // Read and clear the restartService flag
    fun consumeRestartService(): Boolean {
        val v = _restartService.value
        _restartService.value = false
        return v
    }

    // Mark reinitGroupTab as requiring tab reinitialization
    fun makeReinitGroupTab() {
        _reinitGroupTab.value = true
    }

    // Read and clear the reinitGroupTab flag
    fun consumeReinitGroupTab(): Boolean {
        val v = _reinitGroupTab.value
        _reinitGroupTab.value = false
        return v
    }
}
