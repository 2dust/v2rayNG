package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AetherProfileRollbackTest {
    @Test
    fun failedWritesRestoreAllRecordsEvenWhenOneRestoreFails() {
        val restored = mutableListOf<String>()
        val failure = IllegalStateException("save failed")
        val restoreFailure = IllegalStateException("restore failed")
        val thrown = assertThrows(IllegalStateException::class.java) {
            MmkvManager.writeProfileWithRollback(listOf(
                { restored.add("profile") },
                { restored.add("index"); throw restoreFailure },
                { restored.add("selection") },
            )) { throw failure }
        }
        assertSame(failure, thrown)
        assertEquals(listOf("profile", "index", "selection"), restored)
        assertEquals(listOf(restoreFailure), thrown.suppressed.toList())
    }

    @Test
    fun successfulWriteDoesNotRestoreAndReturnsTheSavedValue() {
        var restores = 0
        val result = MmkvManager.writeProfileWithRollback(listOf({ restores++ })) { "profile-guid" }
        assertEquals("profile-guid", result)
        assertEquals(0, restores)
    }
}
