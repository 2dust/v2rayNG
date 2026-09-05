package com.v2ray.ang.ui.compose

import android.app.UiAutomation
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TransientMessageSemanticsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun burstPreservesTheCurrentResultAndKeepsSnackbarInert() {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.toastSuccess(
                "Visible result",
                liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE,
                accessibilityMessage = "Connected to Lab",
            )
            compose.activity.toast("Copied")
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Connected to Lab")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithText("Visible result").assertDoesNotExist()
        compose.onNodeWithText("Copied").assertDoesNotExist()

        // Inspect Android's exported tree too: Compose semantics alone do not establish that a
        // live region survived occlusion or that the visual Snackbar is absent from navigation.
        compose.waitUntil(5000L) {
            findText(automation.rootInActiveWindow, "Connected to Lab") != null
        }
        val nativeResult = findText(automation.rootInActiveWindow, "Connected to Lab")!!
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE, nativeResult.liveRegion)
        assertFalse(nativeResult.isAccessibilityFocused)
        assertFalse(nativeResult.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK))
        assertNull(findText(automation.rootInActiveWindow, "Visible result"))

        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("Copied")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Connected to Lab").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("Copied").assertDoesNotExist()
    }

    @Test
    fun queuedStartAcknowledgementIsPublishedBeforeTheAssertiveResult() {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.toast("Copied")
            compose.activity.toast("Starting service")
            compose.activity.toastSuccess("Connected", AccessibilityLiveRegionMode.ASSERTIVE)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Copied").assertExists()
        compose.onNodeWithText("Starting service").assertDoesNotExist()
        compose.onNodeWithText("Connected").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("Starting service")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Connected").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("Connected")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        compose.onNodeWithText("Starting service").assertDoesNotExist()
    }

    @Test
    fun pausedHostDoesNotReplayPendingAnnouncementsOnResume() {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.toast("First result")
            compose.activity.toast("Pending result")
        }
        compose.mainClock.advanceTimeBy(200)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("First result").assertDoesNotExist()
        compose.onNodeWithText("Pending result").assertDoesNotExist()
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) {
            findText(node.getChild(index), text)?.let { return it }
        }
        return null
    }
}
