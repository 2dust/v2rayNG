package com.v2ray.ang.ui.main

import android.app.UiAutomation
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.ui.compose.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainBottomBarAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation: UiAutomation
        get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun resultBecomesReadableAfterLiveRegionAndHidesDuringTheNextTest() {
        val status = mutableStateOf<MainStatus>(MainStatus.Connected)
        val running = mutableStateOf(true)
        val announcements = MutableSharedFlow<MainTestAnnouncement>(extraBufferCapacity = 4)
        val actions = mutableListOf<MainAction>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var connected: String
            lateinit var disconnected: String
            lateinit var testing: String
            lateinit var resultText: String
            scenario.onActivity { activity ->
                connected = activity.getString(R.string.connection_connected_accessibility)
                disconnected = activity.getString(R.string.connection_not_connected)
                testing = activity.getString(R.string.connection_test_testing)
                resultText = activity.getString(
                    R.string.connection_test_available,
                    activity.resources.getQuantityString(R.plurals.connection_test_delay_accessibility_value, 20, 20L),
                )
                activity.setContent {
                    AppTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            MainBottomBar(
                                displayText = status.value.toString(),
                                accessibilityText = if (running.value) connected else disconnected,
                                status = status.value,
                                testAnnouncements = announcements,
                                formatTestAnnouncement = { if (it == MainStatus.Testing) testing else resultText },
                                isRunning = running.value,
                                isDarkTheme = false,
                                onAction = { actions.add(it) },
                            )
                        }
                    }
                }
            }
            awaitNode { it.isClickable && containsLabel(it, connected) }

            // Identical repeated results must each wait for their own live-region event.
            for (eventId in listOf(1L, 3L)) {
                scenario.onActivity {
                    status.value = MainStatus.Testing
                    assertTrue(announcements.tryEmit(MainTestAnnouncement(eventId, status.value)))
                }
                awaitNode { it.liveRegion == View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE && it.text?.toString() == testing }
                val testingRow = awaitNode { it.isClickable && containsLabel(it, connected) }
                assertFalse(containsLabel(testingRow, testing))
                assertFalse(containsLabel(testingRow, resultText))

                scenario.onActivity {
                    status.value = MainStatus.ConnectionTest(ConnectionTestResult(delayMillis = 20L))
                    assertTrue(announcements.tryEmit(MainTestAnnouncement(eventId + 1, status.value)))
                }
                awaitNode { it.liveRegion == View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE && it.text?.toString() == resultText }
                assertFalse(containsLabel(awaitNode { it.isClickable && containsLabel(it, connected) }, resultText))

                val completedRow = awaitNode {
                    it.isClickable && containsLabel(it, connected) && containsLabel(it, resultText)
                }
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, completedRow.liveRegion)
                assertEquals(1, nodes(completedRow).count { it.text?.toString() == resultText })
                assertTrue(completedRow.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            assertEquals(listOf(MainAction.TestCurrentServer, MainAction.TestCurrentServer), actions)

            scenario.onActivity { status.value = MainStatus.TestProgress("1 / 10") }
            awaitNode { it.isClickable && containsLabel(it, connected) && !containsLabel(it, resultText) }

            scenario.onActivity {
                running.value = false
                status.value = MainStatus.Disconnected
            }
            val disconnectedRow = awaitNode {
                containsLabel(it, disconnected) && AccessibilityNodeInfoCompat.wrap(it).isScreenReaderFocusable
            }
            assertFalse(disconnectedRow.isClickable)
            assertFalse(containsLabel(disconnectedRow, resultText))
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
        throw AssertionError("Expected status or live-region accessibility node was not found")
    }

    private fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        yield(root)
        for (index in 0 until root.childCount) yieldAll(nodes(root.getChild(index)))
    }
}
