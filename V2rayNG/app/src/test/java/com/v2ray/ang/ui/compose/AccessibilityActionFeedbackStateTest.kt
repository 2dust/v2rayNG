package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityActionFeedbackStateTest {
    @Test
    fun repeatedTextStillCreatesANewEvent() {
        val state = AccessibilityActionFeedbackState()
        state.show("Moved")
        val first = state.message!!
        state.show("Moved")
        val second = state.message!!
        assertEquals(first.second, second.second)
        assertNotEquals(first.first, second.first)
    }

    @Test
    fun olderExpiryCannotClearNewerFeedback() {
        val state = AccessibilityActionFeedbackState()
        state.show("First")
        val firstId = state.message!!.first
        state.show("Second")
        state.clear(firstId)
        assertEquals("Second", state.message?.second)
        state.clear(state.message!!.first)
        assertNull(state.message)
    }

    @Test
    fun idsRemainUniqueAfterClearing() {
        val state = AccessibilityActionFeedbackState()
        state.show("Moved")
        val firstId = state.message!!.first
        state.clear(firstId)
        state.show("Moved")
        assertNotEquals(firstId, state.message!!.first)
    }
}
