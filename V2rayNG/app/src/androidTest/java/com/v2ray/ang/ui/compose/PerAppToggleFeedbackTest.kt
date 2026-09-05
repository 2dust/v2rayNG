package com.v2ray.ang.ui.compose

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.perappproxy.PerAppSwitch
import com.v2ray.ang.ui.perappproxy.perAppRoutingDescriptionRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerAppToggleFeedbackTest {
    @Test
    fun settingLabelAndSwitchShareStateFeedback() {
        val probe = ToggleFeedbackProbe()
        val enabled = mutableStateOf(false)
        val bypass = mutableStateOf(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        Column(Modifier.padding(top = 64.dp)) {
                            PerAppSwitch("Enable per-app", enabled.value, { enabled.value = it })
                            PerAppSwitch("Bypass mode", bypass.value, { bypass.value = it })
                        }
                    }
                }
            }
            for (label in listOf("Enable per-app", "Bypass mode")) {
                val row = probe.row(label)
                probe.assertSingleToggle(row)
                assertEquals(1, probe.nodes(row).count { it.isClickable })
                probe.focus(row)
                probe.toggle(row, true)
                probe.toggle(row, false)
                probe.verifyInputModes(row)
            }
        }
    }

    @Test
    fun appCheckboxReportsCheckedStateAndRoutingWithoutRelabeling() {
        val probe = ToggleFeedbackProbe()
        for (enabled in listOf(false, true)) {
            for (bypass in listOf(false, true)) {
                val checked = mutableStateOf(false)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            AppTheme {
                                AppListItem(
                                    appName = "Feedback test app",
                                    packageName = "test.hidden.package",
                                    icon = null,
                                    checked = checked.value,
                                    onCheckedChange = { checked.value = it },
                                    routingDescription = activity.getString(perAppRoutingDescriptionRes(enabled, bypass, checked.value)),
                                    modifier = Modifier.padding(top = 64.dp),
                                )
                            }
                        }
                    }
                    val row = probe.row("Feedback test app")
                    probe.assertSingleToggle(row)
                    assertTrue(probe.nodes(row).any { it.className == "android.widget.CheckBox" })
                    assertEquals(1, probe.nodes(row).count { it.isClickable })
                    probe.focus(row)
                    for (expected in listOf(true, false)) {
                        probe.toggle(row, expected)
                        assertEquals("Feedback test app", probe.label(row))
                        scenario.onActivity { activity ->
                            val state = activity.getString(if (expected) R.string.acc_app_checked else R.string.acc_app_not_checked)
                            val route = activity.getString(perAppRoutingDescriptionRes(enabled, bypass, expected))
                            assertEquals(activity.getString(R.string.acc_app_routing_state, state, route),
                                AccessibilityNodeInfoCompat.wrap(row).stateDescription.toString())
                        }
                    }
                    probe.verifyInputModes(row)
                }
            }
        }
    }
}
