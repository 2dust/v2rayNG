package com.v2ray.ang.root

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RootProcessRunnerTest {
    @Test fun `a silent blocked command respects its deadline`() {
        val start = System.nanoTime()
        val result = RootProcessRunner.run(listOf("sh", "-c", "sleep 5"), 150)
        assertEquals(-1, result.code)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
    }
    @Test fun `inherited stdout cannot hold a completed command open`() {
        val start = System.nanoTime()
        val result = RootProcessRunner.run(listOf("sh", "-c", "sleep 5 & echo complete"), 250)
        assertEquals(0, result.code)
        assertTrue(result.output.contains("complete"))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
    }
    @Test fun `large output is drained without unbounded retention`() {
        val result = RootProcessRunner.run(listOf("sh", "-c", "yes x | head -c 1048576"), 3000, 4096)
        assertEquals(0, result.code)
        assertEquals(4096, result.output.length)
    }
    @Test fun `failure status and diagnostic output survive`() {
        val result = RootProcessRunner.run(listOf("sh", "-c", "echo lock-unavailable >&2; exit 4"), 1000)
        assertEquals(4, result.code)
        assertTrue(result.output.contains("lock-unavailable"))
    }
}
