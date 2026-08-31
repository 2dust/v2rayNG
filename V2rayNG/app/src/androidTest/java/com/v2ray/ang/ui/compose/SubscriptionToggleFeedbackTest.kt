package com.v2ray.ang.ui.compose

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
                assertEquals(4, probe.nodes(row).count { it.isClickable })
                val label = probe.label(row)
                assertTrue(label.contains(name))
                assertTrue(!label.contains("example.invalid"))
                probe.focus(row)
                probe.toggle(row, true)
                assertEquals(true, MmkvManager.decodeSubscription(id)?.enabled)
                probe.toggle(row, false)
                assertEquals(false, MmkvManager.decodeSubscription(id)?.enabled)
                assertEquals(label, probe.label(row))
                probe.verifyInputModes(row)
                for (resource in listOf(R.string.acc_share_named, R.string.acc_edit_named, R.string.acc_delete_named)) {
                    var actionLabel = ""
                    scenario.onActivity { actionLabel = it.getString(resource, name) }
                    probe.awaitNode { it.isClickable && probe.label(it) == actionLabel }
                }
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
