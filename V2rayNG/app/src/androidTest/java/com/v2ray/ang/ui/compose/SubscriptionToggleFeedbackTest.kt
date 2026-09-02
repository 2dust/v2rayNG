package com.v2ray.ang.ui.compose

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.subscription.SubSettingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubscriptionToggleFeedbackTest {
    private fun probeActionLabel(scenario: ActivityScenario<SubSettingActivity>, resource: Int): String {
        var label = ""
        scenario.onActivity { label = it.getString(resource) }
        return label
    }

    @Test
    fun subscriptionRowReportsUpdateStateAndPersistsIt() {
        val probe = ToggleFeedbackProbe()
        val id = UUID.randomUUID().toString()
        val name = "Feedback test subscription"
        MmkvManager.encodeSubscription(id, SubscriptionItem(
            remarks = name, url = "https://example.invalid/feedback", enabled = false, lastUpdated = 1_700_000_000_000L))
        val ids = MmkvManager.decodeSubsList()
        ids.remove(id)
        ids.add(0, id)
        MmkvManager.encodeSubsList(ids)
        try {
            ActivityScenario.launch(SubSettingActivity::class.java).use { scenario ->
                val row = probe.row(name)
                probe.assertSingleToggle(row)
                assertEquals(1, probe.nodes(row).count { it.isClickable })
                val label = probe.label(row)
                assertTrue(label.contains(name))
                assertTrue(!label.contains("example.invalid"))
                probe.focus(row)
                assertEquals(probeActionLabel(scenario, R.string.acc_enable_subscription_update),
                    row.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label)
                probe.toggle(row, true)
                assertEquals(probeActionLabel(scenario, R.string.acc_disable_subscription_update),
                    row.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label)
                assertEquals(true, MmkvManager.decodeSubscription(id)?.enabled)
                probe.toggle(row, false)
                assertEquals(false, MmkvManager.decodeSubscription(id)?.enabled)
                assertEquals(label, probe.label(row))
                probe.verifyInputModes(row)
                var expectedActions = emptyList<String>()
                scenario.onActivity { activity ->
                    expectedActions = listOf(
                        activity.getString(R.string.acc_edit_named, name),
                        activity.getString(R.string.acc_delete_named, name),
                        activity.getString(R.string.acc_enable_subscription_auto_update),
                        activity.getString(R.string.share_subscription_qrcode),
                        activity.getString(R.string.share_subscription_clipboard),
                    )
                }
                assertEquals(expectedActions, row.actionList.filter { it.id != AccessibilityNodeInfo.ACTION_CLICK }.mapNotNull { it.label?.toString() }.take(5))
                scenario.recreate()
                val restored = probe.row(name)
                probe.focus(restored)
                probe.toggle(restored, true)
                assertEquals(true, MmkvManager.decodeSubscription(id)?.enabled)
            }
        } finally {
            MmkvManager.removeSubscription(id)
        }
    }
}
