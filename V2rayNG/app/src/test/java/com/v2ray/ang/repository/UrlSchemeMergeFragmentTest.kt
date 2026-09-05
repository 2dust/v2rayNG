package com.v2ray.ang.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSchemeMergeFragmentTest {

    @Test
    fun appendsHintWhenPayloadHasNoFragment() {
        assertEquals("vmess://abc#MyGroup", mergeFragment("vmess://abc", "MyGroup"))
    }

    @Test
    fun keepsExistingFragment() {
        assertEquals("vmess://abc#Own", mergeFragment("vmess://abc#Own", "MyGroup"))
    }

    @Test
    fun leavesMultiLinePayloadAlone() {
        val batch = "vmess://a\nvmess://b"
        assertEquals(batch, mergeFragment(batch, "MyGroup"))
    }

    @Test
    fun blankHintChangesNothing() {
        assertEquals("vmess://abc", mergeFragment("vmess://abc", "   "))
    }

    @Test
    fun emptyPayloadStaysEmpty() {
        assertEquals("", mergeFragment("", "MyGroup"))
    }
}
