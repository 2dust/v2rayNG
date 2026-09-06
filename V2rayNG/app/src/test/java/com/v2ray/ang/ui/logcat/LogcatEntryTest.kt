package com.v2ray.ang.ui.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatEntryTest {
    @Test
    fun emptyLogProducesNoEntries() {
        assertTrue(createLogcatEntries(emptyList()).isEmpty())
    }

    @Test
    fun identicalTimestampedLinesHaveUniqueKeysWithoutLosingMessages() {
        val line = "08-31 04:32:41.187 I/GoLog (42): repeated message"
        val logs = listOf(line, line, line)
        val entries = createLogcatEntries(logs)

        assertEquals(logs, entries.map { it.text })
        assertEquals(3, entries.map { it.key }.distinct().size)
    }

    @Test
    fun preservesNewestFirstOrderAndOriginalText() {
        val logs = listOf("newest", "\tat repeatedFrame(File.kt:42)", "middle", "\tat repeatedFrame(File.kt:42)")
        val entries = createLogcatEntries(logs)

        assertEquals(logs, entries.map { it.text })
        assertEquals(logs.joinToString("\n"), entries.joinToString("\n") { it.text })
        assertEquals(logs.size, entries.map { it.key }.distinct().size)
    }

    @Test
    fun filteringOtherLinesPreservesKeysOfEveryMatchingOccurrence() {
        val logs = listOf("match", "other", "match", "last")
        val entries = createLogcatEntries(logs)
        val filtered = createLogcatEntries(logs.filter { it.contains("match") })

        assertEquals(entries.filter { it.text.contains("match") }, filtered)
        assertTrue(createLogcatEntries(logs.filter { it.contains("missing") }).isEmpty())
        assertEquals(entries, createLogcatEntries(logs))
    }

    @Test
    fun refreshWithNewerLinesPreservesExistingOccurrenceKeys() {
        val logs = listOf("repeated", "older", "repeated")
        val entries = createLogcatEntries(logs)
        val refreshed = createLogcatEntries(listOf("newest", "repeated") + logs)

        assertEquals(entries, refreshed.takeLast(entries.size))
        assertEquals(refreshed.size, refreshed.map { it.key }.distinct().size)
    }

    @Test
    fun emptyAndKeyLikeMessagesRemainDistinct() {
        val logs = listOf("", "0:", "", "1:message", "message", "message")
        val entries = createLogcatEntries(logs)

        assertEquals(logs, entries.map { it.text })
        assertEquals(logs.size, entries.map { it.key }.distinct().size)
    }
}
