package com.v2ray.ang.ui.main

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainDrawerAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation: UiAutomation
        get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun decorativeHeaderIsHiddenAndNativeDismissActionStillWorks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var menuLabel: String
            lateinit var subscriptionsLabel: String
            lateinit var branding: String
            scenario.onActivity { activity ->
                menuLabel = activity.getString(R.string.acc_open_menu)
                subscriptionsLabel = activity.getString(R.string.title_sub_setting)
                branding = activity.getString(R.string.app_name)
            }

            val menu = awaitNode { it.isClickable && containsLabel(it, menuLabel) }
            assertTrue(menu.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val drawerList = awaitNode {
                it.className == "android.widget.ScrollView" && containsLabel(it, subscriptionsLabel)
            }
            val firstVisibleChild = (0 until drawerList.childCount)
                .mapNotNull(drawerList::getChild)
                .first { it.isVisibleToUser }
            assertTrue("The decorative header must not precede the drawer actions",
                firstVisibleChild.isClickable && containsLabel(firstVisibleChild, subscriptionsLabel))
            assertFalse("Branding must stay hidden from accessibility", nodes(drawerList).any {
                it.isVisibleToUser && (it.text?.toString() == branding || it.contentDescription?.toString() == branding)
            })

            val dismiss = awaitNode { node ->
                node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id }
            }
            assertTrue(dismiss.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id))
            awaitNode { it.isClickable && containsLabel(it, menuLabel) }
        }
    }

    private fun containsLabel(node: AccessibilityNodeInfo, label: String): Boolean = nodes(node).any {
        it.text?.toString() == label || it.contentDescription?.toString() == label
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes(automation.rootInActiveWindow).firstOrNull { it.isVisibleToUser && predicate(it) }
                ?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Expected drawer accessibility node was not found")
    }

    private fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        yield(root)
        for (index in 0 until root.childCount) yieldAll(nodes(root.getChild(index)))
    }
}
