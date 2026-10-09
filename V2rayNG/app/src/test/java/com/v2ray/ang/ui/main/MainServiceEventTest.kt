package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainServiceEventTest {

    @Test
    fun aStateQueryNobodyAcknowledgedMeansThereIsNoService() {
        // The daemon cannot report its own death; silence is how the screen learns of it.
        assertEquals(MainServiceEvent.StateNotRunning, MainServiceEvent.forStateQuery(acknowledged = false))
    }

    @Test
    fun anAcknowledgedStateQueryAddsNothingToTheAnswerOfTheService() {
        assertNull(MainServiceEvent.forStateQuery(acknowledged = true))
    }
}
