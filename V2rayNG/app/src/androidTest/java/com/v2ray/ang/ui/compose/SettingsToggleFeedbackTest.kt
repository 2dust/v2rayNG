package com.v2ray.ang.ui.compose

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsToggleFeedbackTest {
    @Test
    fun checkedChangesEmitStateFeedbackOnTheFocusedRow() {
        val probe = ToggleFeedbackProbe()
        val checked = mutableStateOf(false)
        var changes = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        Column(Modifier.padding(top = 64.dp)) {
                            SettingsSwitchItem(
                                title = "Feedback test switch",
                                summary = "The child switch must not be a separate action target.",
                                checked = checked.value,
                                onCheckedChange = { checked.value = it; changes++ },
                            )
                            SettingsSwitchItem(
                                title = "Disabled test switch",
                                checked = true,
                                enabled = false,
                                onCheckedChange = { error("Disabled control invoked") },
                            )
                        }
                    }
                }
            }
            val row = probe.row("Feedback test switch")
            probe.assertSingleToggle(row)
            assertEquals(1, probe.nodes(row).count { it.isClickable })
            probe.focus(row)
            probe.toggle(row, true)
            probe.toggle(row, false)
            probe.verifyInputModes(row)
            assertEquals(10, changes)

            val disabled = probe.row("Disabled test switch")
            assertFalse(disabled.isEnabled)
            assertEquals(AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE, AccessibilityNodeInfoCompat.wrap(disabled).checked)
            assertFalse(disabled.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
    }
}
