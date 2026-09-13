package com.v2ray.ang.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class TetheringUpstreamMonitorTest {
    @Test
    fun readsTheLatestCallbackSnapshotWithoutRegisteringAnotherCallback() {
        val interfaces = AtomicReference<List<ActiveTetheringInterface>?>(null)
        val received = CountDownLatch(1)
        var closed = false
        val monitor = TetheringUpstreamMonitor(AtomicReference("testtun0"), interfaces, received) { closed = true }
        assertThrows(IllegalStateException::class.java) { monitor.awaitInterfaces(0) }

        val usb = ActiveTetheringInterface(1, "usb0")
        interfaces.set(listOf(usb))
        received.countDown()
        assertEquals(listOf(usb), monitor.awaitInterfaces(0))
        interfaces.set(emptyList())
        assertTrue(monitor.awaitInterfaces(0).isEmpty())

        interfaces.set(null)
        assertThrows(IllegalStateException::class.java) { monitor.awaitInterfaces(0) }
        monitor.close()
        assertTrue(closed)
    }
}
