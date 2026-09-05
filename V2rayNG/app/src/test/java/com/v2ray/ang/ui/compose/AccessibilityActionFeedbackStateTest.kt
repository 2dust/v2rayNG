package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityActionFeedbackStateTest {
    @Test
    fun repeatedTextWaitsForPublicationAndThenCreatesANewEvent() {
        val state = AccessibilityActionFeedbackState().apply { resume() }
        state.show("Moved")
        val first = state.message!!
        state.show("Moved")
        assertEquals(first, state.message)
        state.finish(first.first)
        assertEquals(first, state.message)

        state.published(first.first)
        assertEquals(first.first, state.publishedId)
        state.finish(first.first)
        val second = state.message!!
        assertEquals(first.second, second.second)
        assertNotEquals(first.first, second.first)
        assertNull(state.publishedId)
    }

    @Test
    fun olderExpiryCannotClearNewerFeedback() {
        val state = AccessibilityActionFeedbackState().apply { resume() }
        state.show("First")
        val firstId = state.message!!.first
        state.show("Second")
        state.published(firstId)
        state.finish(firstId)
        val secondId = state.message!!.first
        state.published(firstId)
        state.finish(firstId)
        assertEquals("Second", state.message?.second)
        assertNull(state.publishedId)
        state.published(secondId)
        state.finish(secondId)
        assertNull(state.message)
        assertNull(state.publishedId)
    }

    @Test
    fun idsRemainUniqueAfterClearing() {
        val state = AccessibilityActionFeedbackState().apply { resume() }
        state.show("Moved")
        val firstId = state.message!!.first
        state.published(firstId)
        state.finish(firstId)
        state.show("Moved")
        assertNotEquals(firstId, state.message!!.first)
    }

    @Test
    fun pauseDiscardsCurrentAndPendingFeedbackWithoutReplayingOnResume() {
        val state = AccessibilityActionFeedbackState().apply { resume() }
        state.show("First")
        val firstId = state.message!!.first
        state.published(firstId)
        state.show("Second")
        state.pause()
        state.show("While paused")
        state.published(firstId)
        state.finish(firstId)
        state.resume()
        assertNull(state.message)
        assertNull(state.publishedId)

        state.show("Fresh")
        assertEquals("Fresh", state.message?.second)
        assertNotEquals(firstId, state.message!!.first)
    }

    @Test
    fun inactiveAndBlankMessagesDoNotCreateNodes() {
        val state = AccessibilityActionFeedbackState()
        state.show("Before resume")
        assertNull(state.message)
        state.resume()
        state.show(" ")
        assertNull(state.message)
    }

    @Test
    fun burstIsPublishedInOrder() {
        val state = AccessibilityActionFeedbackState().apply { resume() }
        repeat(5) { state.show("Result $it") }
        repeat(5) { index ->
            val message = state.message!!
            assertEquals("Result $index", message.second)
            state.published(message.first)
            state.finish(message.first)
        }
        assertNull(state.message)
    }
}
