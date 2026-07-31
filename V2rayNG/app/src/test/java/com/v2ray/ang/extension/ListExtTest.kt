package com.v2ray.ang.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListExtTest {

    @Test
    fun moveItem_down_preservesRelativeOrder() {
        val items = mutableListOf("a", "b", "c", "d")

        assertTrue(items.moveItem(0, 2))

        assertEquals(listOf("b", "c", "a", "d"), items)
    }

    @Test
    fun moveItem_up_preservesRelativeOrder() {
        val items = mutableListOf("a", "b", "c", "d")

        assertTrue(items.moveItem(3, 1))

        assertEquals(listOf("a", "d", "b", "c"), items)
    }

    @Test
    fun moveItem_rejectsNoOpAndInvalidIndex() {
        val items = mutableListOf("a", "b", "c")

        assertFalse(items.moveItem(1, 1))
        assertFalse(items.moveItem(-1, 1))
        assertEquals(listOf("a", "b", "c"), items)
    }
}
