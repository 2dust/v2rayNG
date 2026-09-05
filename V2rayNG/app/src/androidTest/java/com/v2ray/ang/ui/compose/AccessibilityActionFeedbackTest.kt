package com.v2ray.ang.ui.compose

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityActionFeedbackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun successfulMovesProduceRepeatablePoliteFeedbackButRejectedMovesDoNot() {
        var acceptMove = true
        var moves = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        val feedback = rememberAccessibilityActionFeedback()
                        val actions = reorderAccessibilityActions(1, 3, feedback) {
                            if (acceptMove) { moves++; true } else false
                        }
                        Scaffold { padding ->
                            Text(
                                "Move feedback target",
                                Modifier.padding(padding).padding(top = 64.dp).semantics { customActions = actions },
                            )
                        }
                    }
                }
            }
            val row = awaitNode { it.text?.toString() == "Move feedback target" }
            assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            val move = row.actionList.first {
                it.label?.toString() == instrumentation.targetContext.getString(R.string.acc_move_up)
            }
            val moved = instrumentation.targetContext.getString(R.string.acc_moved)
            repeat(2) {
                automation.executeAndWaitForEvent(
                    { assertTrue(row.performAction(move.id)) },
                    { event ->
                        val source = event.source
                        event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            source?.liveRegion == android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE &&
                            source.text?.toString() == moved
                    },
                    5_000L,
                )
                assertTrue(row.refresh())
                assertTrue(row.isAccessibilityFocused)
            }
            assertEquals(2, moves)
            SystemClock.sleep(1_200L)
            scenario.onActivity { acceptMove = false }
            assertFalse(row.performAction(move.id))
            SystemClock.sleep(300L)
            assertEquals(2, moves)
            assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString() == moved })
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes(automation.rootInActiveWindow).firstOrNull(predicate)?.let { return it }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Action feedback target not found")
    }

    private fun nodes(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (node == null) return@sequence
        yield(node)
        repeat(node.childCount) { yieldAll(nodes(node.getChild(it))) }
    }
}
