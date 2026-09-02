package com.v2ray.ang.ui.main

import android.app.UiAutomation
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConfigImportResult
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubscriptionImportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val prefix = "http://127.0.0.1:1/${UUID.randomUUID()}"

    @Test
    fun confirmationPreservesDraftOnRecreationAndSavesOnlyAfterOk() = withMain { scenario, viewModel ->
        val url = "$prefix/confirm"
        scenario.onActivity {
            it.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("subscription", url))
        }
        clickText(instrumentation.targetContext.getString(R.string.acc_add))
        clickText(instrumentation.targetContext.getString(R.string.menu_item_import_config_clipboard))
        waitUntil { viewModel.uiState.value.subscriptionImportName != null }
        assertNull(saved(url))

        action(viewModel, MainAction.ChangeSubscriptionImportName("  "))
        action(viewModel, MainAction.ConfirmSubscriptionImport)
        assertNull(saved(url))
        assertEquals("  ", viewModel.uiState.value.subscriptionImportName)

        setDialogText("Travel subscription")
        scenario.recreate()
        waitUntil { viewModel.uiState.value.subscriptionImportName == "Travel subscription" }
        clickText(instrumentation.targetContext.getString(R.string.action_ok))
        waitUntil { !viewModel.isLoading.value }
        val group = saved(url)!!
        assertEquals("Travel subscription", group.subscription.remarks)
        scenario.recreate()
        assertEquals("Travel subscription", MmkvManager.decodeSubscription(group.guid)?.remarks)
        assertNull(viewModel.uiState.value.subscriptionImportName)
    }

    @Test
    fun cancellationAndNumberedDefaultsDoNotCreateUnconfirmedGroups() = withMain { _, viewModel ->
        val base = instrumentation.targetContext.getString(R.string.sub_import_default_name)
        val ids = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        try {
            ids.forEachIndexed { index, id ->
                MmkvManager.encodeSubscription(id, SubscriptionItem(
                    remarks = if (index == 0) base else "$base 2", enabled = false
                ))
            }
            val url = "$prefix/cancel"
            action(viewModel, MainAction.ImportBatchConfig(url))
            waitUntil { viewModel.uiState.value.subscriptionImportName != null }
            assertEquals("$base 3", viewModel.uiState.value.subscriptionImportName)
            clickText(instrumentation.targetContext.getString(R.string.action_cancel))
            waitUntil { !viewModel.isLoading.value }
            assertNull(saved(url))
            assertNull(viewModel.uiState.value.subscriptionImportName)

            action(viewModel, MainAction.ImportBatchConfig(url))
            waitUntil { viewModel.uiState.value.subscriptionImportName != null }
            assertEquals("$base 3", viewModel.uiState.value.subscriptionImportName)
            clickText(instrumentation.targetContext.getString(R.string.action_ok))
            waitUntil { !viewModel.isLoading.value }
            assertEquals("$base 3", saved(url)?.subscription?.remarks)

            action(viewModel, MainAction.ImportBatchConfig("$prefix/next"))
            waitUntil { viewModel.uiState.value.subscriptionImportName != null }
            assertEquals("$base 4", viewModel.uiState.value.subscriptionImportName)
            action(viewModel, MainAction.CancelSubscriptionImport)
            waitUntil { !viewModel.isLoading.value }
            assertNull(saved("$prefix/next"))
        } finally {
            ids.forEach(MmkvManager::removeSubscription)
        }
    }

    @Test
    fun ordinaryProxyImportDoesNotAskForSubscriptionName() = runBlocking {
        val name = "proxy-${UUID.randomUUID()}"
        try {
            val result = AngConfigManager.importBatchConfig(
                "vless://${UUID.randomUUID()}@127.0.0.1:443?security=none#$name", "", true
            ) { _, _ ->
                fail("A proxy profile must not open the subscription naming dialog")
                null
            }
            assertEquals(ConfigImportResult(configCount = 1), result)
            assertTrue(MmkvManager.decodeAllServerList().any { MmkvManager.decodeServerConfig(it)?.remarks == name })
        } finally {
            MmkvManager.decodeAllServerList()
                .filter { MmkvManager.decodeServerConfig(it)?.remarks == name }
                .forEach(MmkvManager::removeServer)
        }
    }

    @Test
    fun encodedBatchPromptsOncePerNewUrlAndCancellationPropagates() = runBlocking {
        try {
            val first = "$prefix/first#Provided%20name"
            val second = "$prefix/second"
            val encoded = Base64.encodeToString("$first\n$first\n$second".toByteArray(), Base64.NO_WRAP)
            val suggestions = mutableListOf<String?>()
            val result = AngConfigManager.importBatchConfig(encoded, "", true) { suggested, _ ->
                suggestions += suggested
                if (suggested == null) null else "Chosen name"
            }
            assertEquals(ConfigImportResult(subscriptionCount = 1), result)
            assertEquals(listOf("Provided name", null), suggestions)
            assertEquals("Chosen name", saved(first)?.subscription?.remarks)
            assertNull(saved(second))
            val duplicate = AngConfigManager.importBatchConfig(first, "", true) { _, _ ->
                fail("An existing URL must not prompt again")
                null
            }
            assertEquals(ConfigImportResult(duplicateSubscriptionCount = 1), duplicate)
            try {
                AngConfigManager.importBatchConfig("$prefix/interrupted", "", true) { _, _ ->
                    throw CancellationException("Test owner ended")
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertNull(saved("$prefix/interrupted"))
            }
        } finally {
            removeTestGroups()
        }
    }

    @Test
    fun duplicateImportExplainsWhyNothingWasAdded() = withMain { _, viewModel ->
        val url = "$prefix/duplicate"
        MmkvManager.encodeSubscription("", SubscriptionItem(remarks = "Existing", url = url, enabled = false))
        val message = instrumentation.targetContext.resources.getQuantityString(
            R.plurals.import_subscription_duplicate, 1
        )

        action(viewModel, MainAction.ImportBatchConfig(url))
        waitUntil {
            findNode(automation.rootInActiveWindow) {
                it.text?.toString() == message
            } != null
        }
        assertNull(viewModel.uiState.value.subscriptionImportName)
        assertEquals(1, MmkvManager.decodeSubscriptions().count { it.subscription.url == url })
    }

    @Test
    fun encodedDuplicatesAndConcurrentImportKeepTheirReason() = runBlocking {
        try {
            val first = "$prefix/duplicate-first"
            val second = "$prefix/duplicate-second"
            listOf(first, second).forEach { url ->
                MmkvManager.encodeSubscription("", SubscriptionItem(remarks = "Existing", url = url, enabled = false))
            }
            val encoded = Base64.encodeToString("$first\n$second\n$first".toByteArray(), Base64.NO_WRAP)
            assertEquals(
                ConfigImportResult(duplicateSubscriptionCount = 2),
                AngConfigManager.importBatchConfig(encoded, "", true) { _, _ ->
                    fail("Existing subscriptions must not prompt for names")
                    null
                }
            )

            val concurrent = "$prefix/concurrent"
            val result = AngConfigManager.importBatchConfig(concurrent, "", true) { _, _ ->
                MmkvManager.encodeSubscription("", SubscriptionItem(remarks = "Other import", url = concurrent, enabled = false))
                "Confirmed name"
            }
            assertEquals(ConfigImportResult(duplicateSubscriptionCount = 1), result)
            assertEquals("Other import", saved(concurrent)?.subscription?.remarks)
            assertEquals(1, MmkvManager.decodeSubscriptions().count { it.subscription.url == concurrent })
            assertEquals(ConfigImportResult(), AngConfigManager.importBatchConfig("not a subscription", "", true))
        } finally {
            removeTestGroups()
        }
    }

    private fun withMain(block: (ActivityScenario<MainActivity>, MainViewModel) -> Unit) {
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var viewModel: MainViewModel
                scenario.onActivity { viewModel = ViewModelProvider(it)[MainViewModel::class.java] }
                assertNull(viewModel.uiState.value.subscriptionImportName)
                block(scenario, viewModel)
            }
        } finally {
            removeTestGroups()
        }
    }

    private fun action(viewModel: MainViewModel, action: MainAction) =
        instrumentation.runOnMainSync { viewModel.onAction(action) }

    private fun saved(url: String) = MmkvManager.decodeSubscriptions().find { it.subscription.url == url }

    private fun removeTestGroups() = MmkvManager.decodeSubscriptions()
        .filter { it.subscription.url.startsWith(prefix) }
        .forEach { MmkvManager.removeSubscription(it.guid) }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for import state" }
            Thread.sleep(50)
        }
    }

    private fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || predicate(node)) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), predicate)?.let { return it }
        }
        return null
    }

    private fun setDialogText(value: String) {
        waitUntil {
            findNode(automation.rootInActiveWindow) { it.isEditable }
                ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }) == true
        }
    }

    private fun clickText(text: String) {
        waitUntil {
            var node = findNode(automation.rootInActiveWindow) {
                it.text?.toString() == text || it.contentDescription?.toString() == text
            }
            while (node != null && !node.isClickable) node = node.parent
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }
}
