package com.v2ray.ang.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DPadFocusTest {

    @Test
    fun lastRowInSingleColumnIsOnlyTheFinalItem() {
        assertFalse(isLastRowItem(index = 3, itemCount = 5, columns = 1))
        assertTrue(isLastRowItem(index = 4, itemCount = 5, columns = 1))
    }

    @Test
    fun lastRowInTwoColumnsIncludesIncompleteRow() {
        assertFalse(isLastRowItem(index = 2, itemCount = 5, columns = 2))
        assertFalse(isLastRowItem(index = 3, itemCount = 5, columns = 2))
        assertTrue(isLastRowItem(index = 4, itemCount = 5, columns = 2))
    }

    @Test
    fun lastRowInTwoColumnsIncludesBothItemsWhenEven() {
        assertFalse(isLastRowItem(index = 1, itemCount = 4, columns = 2))
        assertTrue(isLastRowItem(index = 2, itemCount = 4, columns = 2))
        assertTrue(isLastRowItem(index = 3, itemCount = 4, columns = 2))
    }

    @Test
    fun lastRowRejectsEmptyOrInvalidInput() {
        assertFalse(isLastRowItem(index = 0, itemCount = 0, columns = 1))
        assertFalse(isLastRowItem(index = 0, itemCount = 2, columns = 0))
        assertFalse(isLastRowItem(index = -1, itemCount = 2, columns = 1))
        assertFalse(isLastRowItem(index = 2, itemCount = 2, columns = 1))
    }
}
