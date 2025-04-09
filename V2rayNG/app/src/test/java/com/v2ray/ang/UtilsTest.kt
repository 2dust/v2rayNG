package com.v2ray.ang

import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UtilsTest {

    @Test
    fun test_parseInt() {
        assertEquals(Utils.parseInt("1234"), 1234)
    }

    @Test
    fun test_isIpAddress() {
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        assertTrue(Utils.isIpAddress("127.0.0.1"))
        assertTrue(Utils.isIpAddress("127.0.0.1:80"))
        assertTrue(Utils.isIpAddress("0.0.0.0/0"))
        assertTrue(Utils.isIpAddress("::1"))
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
        assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80"))
        assertTrue(Utils.isIpAddress("1::"))
        assertTrue(Utils.isIpAddress("::"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("10.24.56.0/24"))
        assertTrue(Utils.isIpAddress("2001:4321::1"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
    }

    @Test
    fun test_IsIpInCidr() {
        assertTrue(Utils.isIpInCidr("192.168.1.1", "192.168.1.0/24"))
        assertTrue(Utils.isIpInCidr("192.168.1.254", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.2.1", "192.168.1.0/24"))

        assertTrue(Utils.isIpInCidr("10.0.0.0", "10.0.0.0/8"))
        assertTrue(Utils.isIpInCidr("10.255.255.255", "10.0.0.0/8"))
        assertFalse(Utils.isIpInCidr("11.0.0.0", "10.0.0.0/8"))

        assertFalse(Utils.isIpInCidr("invalid-ip", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.1.1", "invalid-cidr"))
    }

}