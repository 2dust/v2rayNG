package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainViewModelStatusTest {

    private val connecting = MainStatus.Connecting("The Aether core is still connecting")

    @Test
    fun aStartOrStopSignalSetsThePlainState() {
        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(MainStatus.Disconnected, wasRunning = false, running = true, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(MainStatus.Connected, wasRunning = true, running = false, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = true, clearTestingText = true)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = false, clearTestingText = true)
        )
    }

    @Test
    fun aRepeatedSignalKeepsTestTextButEndsConnecting() {
        val progress = MainStatus.TestProgress("3 / 10")
        assertEquals(progress, MainViewModel.runningStatus(progress, wasRunning = true, running = true, clearTestingText = false))

        val result = MainStatus.ConnectionTest(ConnectionTestResult(delayMillis = 120L, errorMessage = ""))
        assertEquals(result, MainViewModel.runningStatus(result, wasRunning = true, running = true, clearTestingText = false))

        assertEquals(
            MainStatus.Connected,
            MainViewModel.runningStatus(connecting, wasRunning = true, running = true, clearTestingText = false)
        )
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(connecting, wasRunning = false, running = false, clearTestingText = false)
        )
    }

    @Test
    fun aServiceFoundGoneOnAStateQueryEndsWhateverTheScreenWasLeftShowing() {
        // The query nobody acknowledged arrives as a plain "not running" signal, without clearing test text.
        val left = listOf(MainStatus.Connected, connecting, MainStatus.TestProgress("3 / 10"))
        for (status in left) {
            assertEquals(
                MainStatus.Disconnected,
                MainViewModel.runningStatus(status, wasRunning = true, running = false, clearTestingText = false)
            )
        }
        // A screen that already shows a stopped service keeps what it shows.
        assertEquals(
            MainStatus.Disconnected,
            MainViewModel.runningStatus(MainStatus.Disconnected, wasRunning = false, running = false, clearTestingText = false)
        )
    }
}
