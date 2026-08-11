package com.v2ray.ang

import com.v2ray.ang.util.SubscriptionUserInfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionUserInfoParserTest {
    @Test
    fun parsesTrafficAndExpirationHeader() {
        val result = SubscriptionUserInfoParser.parse("upload=1024; download=2048; total=4096; expire=1700000000")

        requireNotNull(result)
        assertEquals(1024, result.upload)
        assertEquals(2048, result.download)
        assertEquals(4096, result.total)
        assertEquals(1700000000000, result.expire)
    }

    @Test
    fun ignoresMalformedAndNegativeValues() {
        val result = SubscriptionUserInfoParser.parse("upload=-1; download=nope; total=8192; ignored=4")

        requireNotNull(result)
        assertEquals(0, result.upload)
        assertEquals(0, result.download)
        assertEquals(8192, result.total)
        assertEquals(-1, result.expire)
    }

    @Test
    fun returnsNullWhenHeaderHasNoKnownFields() {
        assertNull(SubscriptionUserInfoParser.parse("foo=123;bar=456"))
    }
}
