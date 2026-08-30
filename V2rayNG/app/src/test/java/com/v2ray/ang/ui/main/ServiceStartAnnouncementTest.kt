package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceStartAnnouncementTest {

    @Test
    fun usesTheDaemonSnapshotAfterTheSelectedNameChanges() {
        var selectedName = "Server A"
        val event = MainServiceEvent.StateStartSuccess(selectedName)
        selectedName = "Server B"

        assertEquals("Server B", selectedName)
        assertEquals(
            "Connected to Server A",
            event.accessibilityMessage { "Connected to $it" },
        )
    }

    @Test
    fun trimsTheDaemonNameBeforeFormatting() {
        val event = MainServiceEvent.StateStartSuccess("  Server A  ")

        assertEquals("Connected to Server A", event.accessibilityMessage { "Connected to $it" })
    }

    @Test
    fun missingOrBlankNamesKeepTheOrdinarySuccessMessage() {
        for (name in listOf("", " \t\n")) {
            assertNull(
                MainServiceEvent.StateStartSuccess(name).accessibilityMessage {
                    error("A blank name must not be announced")
                }
            )
        }
    }
}
