package com.v2ray.ang.ui.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/** Native-event checks; run with TalkBack enabled, without suppressing accessibility services. */
internal class ToggleFeedbackProbe {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    init {
        val manager = instrumentation.targetContext.getSystemService(AccessibilityManager::class.java)
        assumeTrue("Enable TalkBack before this test", manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_SPOKEN
        ).isNotEmpty())
    }

    fun row(label: String): AccessibilityNodeInfo {
        // Let the activity's initial window/focus events finish before testing a toggle.
        automation.waitForIdle(500L, 5_000L)
        return awaitNode {
            it.isVisibleToUser && it.isCheckable && nodes(it).any { child ->
                child.text?.contains(label) == true || child.contentDescription?.contains(label) == true
            }
        }
    }

    fun assertSingleToggle(row: AccessibilityNodeInfo) {
        assertEquals(1, nodes(row).count { it.isCheckable })
        assertEquals(1, row.actionList.count { it.id == AccessibilityNodeInfo.ACTION_CLICK })
    }

    fun focus(row: AccessibilityNodeInfo) {
        assertTrue(row.isAccessibilityFocused || row.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
    }

    fun label(row: AccessibilityNodeInfo): String =
        nodes(row).mapNotNull { it.contentDescription?.toString() }.joinToString()

    fun toggle(row: AccessibilityNodeInfo, expected: Boolean, retainAccessibilityFocus: Boolean = true, action: () -> Unit = {
        assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }) {
        val before = AccessibilityNodeInfoCompat.wrap(row).stateDescription.toString()
        automation.executeAndWaitForEvent(
            action,
            { event ->
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    event.contentChangeTypes and AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION != 0 &&
                    event.source == row
            },
            3_000L,
        )
        assertTrue(row.refresh())
        assertEquals(
            if (expected) AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE else AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE,
            AccessibilityNodeInfoCompat.wrap(row).checked,
        )
        if (retainAccessibilityFocus) assertTrue("Accessibility click must retain row focus", row.isAccessibilityFocused)
        assertTrue(before != AccessibilityNodeInfoCompat.wrap(row).stateDescription.toString())
    }

    fun tap(row: AccessibilityNodeInfo, trailing: Boolean = false) {
        val bounds = Rect().also(row::getBoundsInScreen)
        val x = if (trailing) bounds.right - bounds.height() / 4f else bounds.left + bounds.width() / 4f
        val y = if (trailing) bounds.bottom - bounds.height() / 4f else bounds.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    fun key(row: AccessibilityNodeInfo, keyCode: Int) {
        assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        instrumentation.sendKeyDownUpSync(keyCode)
    }

    fun verifyInputModes(row: AccessibilityNodeInfo) {
        // Switching to pointer/keyboard input can legitimately clear accessibility focus.
        toggle(row, true, retainAccessibilityFocus = false) { tap(row) }
        toggle(row, false, retainAccessibilityFocus = false) { tap(row, trailing = true) }
        for (keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)) {
            toggle(row, true, retainAccessibilityFocus = false) { key(row, keyCode) }
            toggle(row, false, retainAccessibilityFocus = false) { key(row, keyCode) }
        }
    }

    fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes(automation.rootInActiveWindow).firstOrNull(predicate)?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Native accessibility node not found")
    }

    fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        yield(root)
        for (index in 0 until root.childCount) yieldAll(nodes(root.getChild(index)))
    }
}
