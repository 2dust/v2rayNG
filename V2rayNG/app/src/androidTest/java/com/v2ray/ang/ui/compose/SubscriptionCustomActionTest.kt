package com.v2ray.ang.ui.compose

import android.app.UiAutomation
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.subscription.SubSettingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubscriptionCustomActionTest {
    @Test
    fun periodicUpdateActionPersistsAndAnnouncesItsResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val probe = ToggleFeedbackProbe()
        val id = UUID.randomUUID().toString()
        val name = "Periodic action subscription"
        val initial = SubscriptionItem(
            remarks = name, url = "https://example.invalid/periodic-action",
            enabled = false, autoUpdate = false, lastUpdated = System.currentTimeMillis(),
        )
        MmkvManager.encodeSubscription(id, initial)
        val ids = MmkvManager.decodeSubsList().apply { remove(id); add(0, id) }
        MmkvManager.encodeSubsList(ids)
        try {
            ActivityScenario.launch(SubSettingActivity::class.java).use { scenario ->
                for (enabled in listOf(true, false)) {
                    val row = probe.row(name)
                    probe.focus(row)
                    val context = instrumentation.targetContext
                    val actionLabel = context.getString(if (enabled) R.string.acc_enable_subscription_auto_update
                        else R.string.acc_disable_subscription_auto_update)
                    val result = context.getString(if (enabled) R.string.acc_subscription_auto_update_enabled
                        else R.string.acc_subscription_auto_update_disabled)
                    val action = row.actionList.single { it.label?.toString() == actionLabel }
                    automation.executeAndWaitForEvent(
                        { assertTrue(row.performAction(action.id)) },
                        { event ->
                            val source = event.source
                            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                                source?.liveRegion == android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE &&
                                source.text?.toString() == result
                        },
                        5_000L,
                    )
                    assertEquals(initial.copy(autoUpdate = enabled), MmkvManager.decodeSubscription(id))
                    assertTrue(row.refresh())
                    assertTrue(row.isAccessibilityFocused)
                    scenario.recreate()
                }
            }
        } finally {
            SubscriptionUpdater.cancelOne(subId = id)
            MmkvManager.removeSubscription(id)
        }
    }
}
