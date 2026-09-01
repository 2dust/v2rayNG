package com.v2ray.ang.ui.server

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProxyChainMemberAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun memberFieldOwnsRemoveAndReorderActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Surface {
                            ProxyChainScreen(
                                editGuid = "",
                                isRunning = false,
                                initialRemarks = "Accessibility chain",
                                initialMembers = listOf("One", "Two", "Three"),
                                allRemarks = listOf("One", "Two", "Three"),
                                onBackClick = {},
                                onSave = { _, _ -> true },
                                onDelete = {},
                            )
                        }
                    }
                }
            }

            val expectedLabels = with(instrumentation.targetContext) {
                listOf(
                    getString(R.string.acc_remove),
                    getString(R.string.acc_move_to_top),
                    getString(R.string.acc_move_up),
                    getString(R.string.acc_move_down),
                    getString(R.string.acc_move_to_bottom),
                )
            }
            val two = awaitMember("Two") { node ->
                node.actionList.mapNotNull { it.label?.toString() } == expectedLabels
            }
            assertTrue(two.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
            assertEquals(0, nodes().count { it.contentDescription?.toString() == expectedLabels.first() })
            assertTrue(two.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
            val moveToTop = two.actionList.first { it.label?.toString() == expectedLabels[1] }
            assertTrue(two.performAction(moveToTop.id))

            val moved = awaitMember("Two") { node ->
                node.actionList.none { it.label?.toString() == expectedLabels[1] }
            }
            assertTrue(moved.isFocused)
            awaitMember("One") { node ->
                node.actionList.any { it.label?.toString() == expectedLabels[1] }
            }
        }
    }

    private fun awaitMember(
        value: String,
        predicate: (AccessibilityNodeInfo) -> Boolean = { true },
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { node -> node.text?.toString() == value && predicate(node) }?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Proxy-chain member did not reach the expected accessibility state")
    }

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
        }
        automation.rootInActiveWindow?.let(::visit)
    }
}
