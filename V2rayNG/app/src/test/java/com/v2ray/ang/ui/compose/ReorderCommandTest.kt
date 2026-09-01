package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderCommandTest {
    @Test
    fun exposesOnlyCommandsThatChangeThePosition() {
        assertEquals(emptyList<ReorderCommand>(), ReorderCommand.availableAt(0, 1))
        assertEquals(
            listOf(ReorderCommand.MoveDown, ReorderCommand.MoveToBottom),
            ReorderCommand.availableAt(0, 4),
        )
        assertEquals(ReorderCommand.entries, ReorderCommand.availableAt(1, 4))
        assertEquals(
            listOf(ReorderCommand.MoveToTop, ReorderCommand.MoveUp),
            ReorderCommand.availableAt(3, 4),
        )
    }

    @Test
    fun resolvesDestinationFromTheCurrentListPosition() {
        assertEquals(0, ReorderCommand.MoveToTop.targetIndex(2, 4))
        assertEquals(1, ReorderCommand.MoveUp.targetIndex(2, 4))
        assertEquals(3, ReorderCommand.MoveDown.targetIndex(2, 4))
        assertEquals(3, ReorderCommand.MoveToBottom.targetIndex(2, 4))
        assertNull(ReorderCommand.MoveUp.targetIndex(0, 4))
        assertNull(ReorderCommand.MoveDown.targetIndex(3, 4))
        assertNull(ReorderCommand.MoveToTop.targetIndex(-1, 4))
    }
}
