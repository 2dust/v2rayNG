package com.v2ray.ang.ui.xfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class XfinderLogStoreTest {
    @BeforeEach
    fun reset() = XfinderLogStore.clear()

    @Test
    fun startsEmptyAndClearEmptiesIt() {
        assertTrue(XfinderLogStore.lines.value.isEmpty())
        XfinderLogStore.info("a")
        XfinderLogStore.clear()
        assertTrue(XfinderLogStore.lines.value.isEmpty())
    }

    @Test
    fun appendsInOrderWithLevel() {
        XfinderLogStore.info("first")
        XfinderLogStore.error("second")
        val lines = XfinderLogStore.lines.value
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("INFO") && lines[0].endsWith("first"))
        assertTrue(lines[1].contains("ERROR") && lines[1].endsWith("second"))
    }

    @Test
    fun keepsOnlyTheNewestMaxLines() {
        repeat(XfinderLogStore.MAX_LINES + 25) { XfinderLogStore.info("line $it") }
        val lines = XfinderLogStore.lines.value
        assertEquals(XfinderLogStore.MAX_LINES, lines.size)
        assertTrue(lines.last().endsWith("line ${XfinderLogStore.MAX_LINES + 24}"))
        assertTrue(lines.first().endsWith("line 25"))
    }

    @Test
    fun formatLineLayout() {
        assertEquals("[12:00:01] INFO  hello", XfinderLogStore.formatLine("12:00:01", "INFO", "hello"))
    }
}
