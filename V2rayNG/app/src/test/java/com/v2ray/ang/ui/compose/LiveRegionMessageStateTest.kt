package com.v2ray.ang.ui.compose

import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRegionMessageStateTest {

    @Test
    fun mirrorsEveryMessageTypeToAPoliteLiveRegionByDefault() {
        for (type in ToastType.entries) {
            val state = LiveRegionMessageState { 0L }
            assertNull(state.current)
            state.offer(AppSnackbarMessage(message = "Message", type = type))

            assertEquals("Message", state.current?.text)
            assertEquals(AccessibilityLiveRegionMode.POLITE, state.current?.mode)
        }
    }

    @Test
    fun ignoresBlankMessages() {
        val state = LiveRegionMessageState { 0L }
        state.offer(AppSnackbarMessage(message = " "))
        assertNull(state.current)

        state.offer(AppSnackbarMessage(message = "Result"))
        state.offer(AppSnackbarMessage(message = "\n"))
        assertEquals("Result", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }

    @Test
    fun suppressesOnlyRecentDuplicatesWithTheSameMode() {
        var now = 100L
        val state = LiveRegionMessageState { now }
        val polite = AppSnackbarMessage(message = "Service state")
        state.offer(polite)
        state.offer(polite)
        state.advance(state.current!!.id)
        assertNull(state.current)

        val assertive = polite.copy(liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE)
        state.offer(assertive)
        assertEquals(AccessibilityLiveRegionMode.ASSERTIVE, state.current?.mode)
        state.advance(state.current!!.id)

        now += DuplicateLiveRegionMessageWindowMs
        state.offer(assertive)
        assertEquals("Service state", state.current?.text)
    }

    @Test
    fun usesTheAccessibilityMessageWithoutChangingTheVisibleMessage() {
        val state = LiveRegionMessageState { 0L }
        val event = AppSnackbarMessage(
            message = "Service started successfully",
            liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE,
            accessibilityMessage = "Service started successfully. Now connected to Lab.",
        )
        state.offer(event)

        assertEquals("Service started successfully. Now connected to Lab.", state.current?.text)
        assertEquals("Service started successfully", event.message)
    }

    @Test
    fun aBurstCannotReplaceTheCurrentServiceResultBeforePublication() {
        val state = LiveRegionMessageState { 0L }
        state.offer(AppSnackbarMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        val resultId = state.current!!.id
        state.offer(AppSnackbarMessage("Copied"))
        state.offer(AppSnackbarMessage("Updated"))

        assertEquals("Connected", state.current?.text)
        state.advance(resultId)
        assertEquals("Copied", state.current?.text)
        // A cancelled expiry must not dismiss the next message.
        state.advance(resultId)
        assertEquals("Copied", state.current?.text)
        state.advance(state.current!!.id)
        assertEquals("Updated", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }

    @Test
    fun pendingServiceResultsTakePriorityWithoutReorderingEachPriority() {
        val state = LiveRegionMessageState { 0L }
        state.offer(AppSnackbarMessage("Downloading"))
        state.offer(AppSnackbarMessage("Copied"))
        state.offer(AppSnackbarMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        state.offer(AppSnackbarMessage("Stopped", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        assertEquals("Downloading", state.current?.text)

        for (expected in listOf("Connected", "Stopped", "Copied")) {
            state.advance(state.current!!.id)
            assertEquals(expected, state.current?.text)
        }
    }

    @Test
    fun leavingTheResumedHostDropsPendingFeedbackAndResetsDeduplication() {
        val state = LiveRegionMessageState { 0L }
        val message = AppSnackbarMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE)
        state.offer(message)
        val previousId = state.current!!.id
        state.offer(AppSnackbarMessage("Copied"))
        state.clear()
        assertNull(state.current)

        state.offer(message)
        state.advance(previousId)
        assertEquals("Connected", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }
}
