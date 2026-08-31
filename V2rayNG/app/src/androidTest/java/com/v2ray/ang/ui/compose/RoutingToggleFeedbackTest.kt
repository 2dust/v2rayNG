package com.v2ray.ang.ui.compose

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.routing.RoutingRulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingToggleFeedbackTest {
    @Test
    fun rowOwnsToggleWhileEditAndDeleteRemainIndependent() {
        val probe = ToggleFeedbackProbe()
        val rule = mutableStateOf(RulesetItem(id = "feedback-rule", remarks = "Feedback test rule",
            outboundTag = AppConfig.TAG_DIRECT, enabled = false, locked = true))
        var changes = 0
        var edits = 0
        var deletes = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        Column(Modifier.padding(top = 64.dp)) {
                            RoutingRulesetItem(rule.value,
                                onEdit = { edits++ },
                                onDelete = { deletes++ },
                                onEnabledChange = { rule.value = rule.value.copy(enabled = it); changes++ })
                        }
                    }
                }
            }
            val row = probe.row("Feedback test rule")
            probe.assertSingleToggle(row)
            assertEquals(3, probe.nodes(row).count { it.isClickable })
            probe.focus(row)
            val label = probe.label(row)
            probe.toggle(row, true)
            probe.toggle(row, false)
            assertEquals(label, probe.label(row))
            probe.verifyInputModes(row)
            assertEquals(10, changes)
            for (resource in listOf(R.string.acc_edit_routing_rule_named, R.string.acc_delete_routing_rule_named)) {
                var actionLabel = ""
                scenario.onActivity { actionLabel = it.getString(resource, "Feedback test rule") }
                val action = probe.awaitNode { it.isClickable && probe.label(it) == actionLabel }
                assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            assertEquals(1, edits)
            assertEquals(1, deletes)
            assertEquals(10, changes)
        }
    }
}
