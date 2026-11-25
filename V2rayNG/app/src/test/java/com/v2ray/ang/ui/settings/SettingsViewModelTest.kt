package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SettingsViewModelTest {
    @Test
    fun editsAndReloadsTheDefaultHopInterval() = runBlocking {
        val viewModel = SettingsViewModel(mock())
        var stored: String? = null
        val write: (String) -> Boolean = { stored = it; true }
        viewModel.loadHysteria2HopInterval { stored }
        assertEquals("30", viewModel.hysteria2HopInterval.value)
        assertTrue(viewModel.saveHysteria2HopInterval(" 30-60 ", write))
        assertEquals("30-60", stored)
        assertFalse(viewModel.saveHysteria2HopInterval("45") { false })
        assertEquals("30-60", viewModel.hysteria2HopInterval.value)
        val reopened = SettingsViewModel(mock())
        reopened.loadHysteria2HopInterval { stored }
        assertEquals("30-60", reopened.hysteria2HopInterval.value)
        assertTrue(reopened.saveHysteria2HopInterval("", write))
        assertEquals("30", stored)
    }

    @Test
    fun vpnSettingsVisibilityFollowsActivityAvailability() = runBlocking(Dispatchers.IO) {
        val packageManager = mock<PackageManager>()
        val application = mock<Application>()
        whenever(application.packageManager).thenReturn(packageManager)
        var activity: ComponentName? = null

        mockConstruction(Intent::class.java) { intent, context ->
            assertEquals(listOf(Settings.ACTION_VPN_SETTINGS), context.arguments())
            whenever(intent.resolveActivity(packageManager)).thenAnswer { activity }
        }.use {
            val viewModel = SettingsViewModel(application)
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)

            activity = mock<ComponentName>()
            viewModel.refreshSystemVpnSettingsAvailability()
            assertTrue(viewModel.systemVpnSettingsAvailable.value)

            activity = null
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
        }
    }
}
