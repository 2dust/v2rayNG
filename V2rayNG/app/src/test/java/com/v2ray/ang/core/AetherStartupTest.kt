package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.AetherSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AetherStartupTest {
    @Test
    fun legacyConfiguredPortsNeverDetermineTheRuntimeListener() {
        listOf("1819", "10808", "80", "", "bad").forEach { legacy ->
            val arguments = AetherSettings(socksPort = legacy).arguments("/private/identity.toml", 45123)
            assertEquals("127.0.0.1:45123", arguments[arguments.indexOf("--bind") + 1])
        }
        listOf(0, 80, 65536).forEach { port ->
            assertThrows(IllegalArgumentException::class.java) { AetherSettings().arguments("identity.toml", port) }
        }
    }

    @Test
    fun nativeOutputRetainsOnlyThePhaseAndExitCode() {
        var stage = AetherStartupStage.CORE
        stage = stage.observe("register identity credential=DO_NOT_LOG")
        assertEquals(AetherStartupStage.REGISTRATION, stage)
        stage = stage.observe("identity ready: device=DO_NOT_LOG")
        assertEquals(AetherStartupStage.SCAN, stage)
        stage = stage.observe("handshake failed private-key=DO_NOT_LOG")
        assertEquals(AetherStartupStage.TRANSPORT, stage)
        val failure = AetherStartupException(stage, 1)
        assertEquals(1, failure.exitCode)
        assertFalse(failure.message.orEmpty().contains("DO_NOT_LOG"))
        assertEquals(stage, stage.observe("unrelated diagnostic text"))
    }

    @Test
    fun timeoutIsDistinctFromAnExitedCore() {
        val failure = AetherStartupException(AetherStartupStage.SCAN)
        assertNull(failure.exitCode)
        assertEquals(AetherStartupStage.SCAN, failure.stage)
    }
}
