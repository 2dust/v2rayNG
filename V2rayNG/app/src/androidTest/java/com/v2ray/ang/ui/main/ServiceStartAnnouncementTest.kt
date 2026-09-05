package com.v2ray.ang.ui.main

import android.content.res.Configuration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.serviceStartedMessage
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class ServiceStartAnnouncementTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun serviceEventAnnouncesTheDaemonSnapshotRatherThanTheCurrentSelection() {
        val viewModel = compose.runOnIdle {
            ViewModelProvider(compose.activity)[MainViewModel::class.java]
        }
        val previousSelection = MmkvManager.getSelectServer()
        val startedGuid = UUID.randomUUID().toString()
        val selectedGuid = UUID.randomUUID().toString()
        val handled = AtomicReference<Boolean?>(null)
        val context = AppLocaleManager.localizedContext(compose.activity)
        val expected = context.getString(R.string.acc_service_started_connected_to, "Server A")
        val wrongSelection = context.getString(R.string.acc_service_started_connected_to, "Server B")

        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                MmkvManager.encodeServerConfig(
                    startedGuid, ProfileItem(configType = EConfigType.SOCKS, remarks = "  Server A  "),
                )
                MmkvManager.encodeServerConfig(
                    selectedGuid, ProfileItem(configType = EConfigType.SOCKS, remarks = "Server B"),
                )
                viewModel.updateSelectedGuid(startedGuid)
                val daemonSnapshot = MmkvManager.decodeServerConfig(startedGuid)!!.remarks
                viewModel.updateSelectedGuid(selectedGuid)
                assertEquals(selectedGuid, viewModel.uiState.value.selectedGuid)
                assertEquals("Server B", MmkvManager.decodeServerConfig(MmkvManager.getSelectServer()!!)!!.remarks)

                // Exercise the real ordered broadcast receiver, event flow, ViewModel and host.
                MessageHelper.sendMsg2UIForResult(
                    compose.activity, AppConfig.MSG_STATE_START_SUCCESS, daemonSnapshot,
                ) { handled.set(it) }
            }
            compose.waitUntil(5000L) { handled.get() != null }
            assertEquals(true, handled.get())
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithText(expected)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            compose.onNodeWithText(wrongSelection).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.toast_services_success)).assertDoesNotExist()
        } finally {
            compose.runOnIdle {
                MmkvManager.removeServer(startedGuid)
                MmkvManager.removeServer(selectedGuid)
                if (previousSelection != null) viewModel.updateSelectedGuid(previousSelection)
                else viewModel.refreshSelectedGuid()
            }
        }
    }

    @Test
    fun sharedFormatterTrimsNamesAndHandlesBlankNamesInEverySupportedLocale() {
        for (language in listOf("en", "ar", "bn", "bqi-IR", "fa", "ru", "vi", "zh-CN", "zh-TW")) {
            val configuration = Configuration(compose.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val context = compose.activity.createConfigurationContext(configuration)
            assertEquals(
                language,
                context.getString(R.string.acc_service_started_connected_to, "Server A"),
                context.serviceStartedMessage("  Server A  "),
            )
            for (name in listOf("", " \t\n")) {
                assertEquals(
                    language,
                    context.getString(R.string.toast_services_success),
                    context.serviceStartedMessage(name),
                )
            }
        }
    }
}
