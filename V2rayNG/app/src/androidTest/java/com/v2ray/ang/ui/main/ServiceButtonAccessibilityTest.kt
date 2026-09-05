package com.v2ray.ang.ui.main

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceButtonAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun clickLabelTracksConnectionStateWithoutReplacingNativeActivation() {
        val running = mutableStateOf(false)
        val dispatched = mutableListOf<MainAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            MainBottomBar(
                                displayText = "Status",
                                isRunning = running.value,
                                isDarkTheme = false,
                                onAction = {
                                    dispatched.add(it)
                                    running.value = !running.value
                                },
                            )
                        }
                    }
                }
            }
            val original = awaitButton(false)
            original.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            assertTrue(awaitButton(false).isAccessibilityFocused)
            assertClickLabel(original, running = false)
            assertTrue(original.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertClickLabel(awaitButton(true), running = true)
            assertEquals(original, awaitButton(true))
            assertTrue(awaitButton(true).isAccessibilityFocused)

            val bounds = Rect().also { awaitButton(true).getBoundsInScreen(it) }
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(
                    time, SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0,
                )
                automation.injectInputEvent(event, true)
                event.recycle()
            }
            instrumentation.waitForIdleSync()
            assertClickLabel(awaitButton(false), running = false)
            for (keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)) {
                val wasRunning = running.value
                val expectedRunning = !wasRunning
                assertTrue(awaitButton(wasRunning).performAction(AccessibilityNodeInfo.ACTION_FOCUS))
                instrumentation.sendKeyDownUpSync(keyCode)
                instrumentation.waitForIdleSync()
                assertClickLabel(awaitButton(expectedRunning), expectedRunning)
            }
            assertEquals(List(5) { MainAction.ToggleService }, dispatched)
        }
    }

    private fun assertClickLabel(node: AccessibilityNodeInfo, running: Boolean) {
        assertTrue(node.isClickable)
        assertTrue(nodes(node).any { it.className.toString() == "android.widget.Button" })
        assertEquals(
            instrumentation.targetContext.getString(if (running) R.string.acc_disconnect else R.string.acc_connect),
            node.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label?.toString(),
        )
        assertEquals(1, nodes(node).count { it.isClickable })
    }

    private fun awaitButton(running: Boolean): AccessibilityNodeInfo {
        val name = instrumentation.targetContext.getString(if (running) R.string.acc_stop else R.string.acc_start)
        val clickLabel = instrumentation.targetContext.getString(if (running) R.string.acc_disconnect else R.string.acc_connect)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            // Wait for the fixture, not the activity's preceding real service button.
            if (nodes(root).none { it.text?.toString() == "Status" || it.contentDescription?.toString() == "Status" }) {
                SystemClock.sleep(50L)
                continue
            }
            nodes(root).firstOrNull {
                it.isClickable &&
                    nodes(it).any { child -> child.contentDescription?.toString() == name } &&
                    it.actionList.any { action ->
                        action.id == AccessibilityNodeInfo.ACTION_CLICK && action.label?.toString() == clickLabel
                    }
            }?.let { return it }
            SystemClock.sleep(50L)
        }
        throw AssertionError("Service button not found: $name\n" + nodes(automation.rootInActiveWindow).joinToString("\n") {
            "${it.className}: text=${it.text}, description=${it.contentDescription}, clickable=${it.isClickable}, actions=${it.actionList}"
        })
    }

    private fun nodes(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (node == null) return@sequence
        yield(node)
        repeat(node.childCount) { yieldAll(nodes(node.getChild(it))) }
    }
}
