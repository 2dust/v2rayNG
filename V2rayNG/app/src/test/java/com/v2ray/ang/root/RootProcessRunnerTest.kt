package com.v2ray.ang.root

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RootProcessRunnerTest {
    @Test fun `a silent blocked command respects its deadline`() {
        val start = System.nanoTime()
        val result = RootProcessRunner.run(RootProcessTestCommand.command("sleep"), 150)
        assertEquals(-1, result.code)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
    }
    @Test fun `inherited stdout cannot hold a completed command open`() {
        val start = System.nanoTime()
        // Allow JVM startup, but return before the descendant releases stdout after five seconds.
        val result = RootProcessRunner.run(RootProcessTestCommand.command("inherited-stdout"), 2000)
        assertEquals(0, result.code)
        assertTrue(result.output.contains("complete"))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 4000)
    }
    @Test fun `large output is drained without unbounded retention`() {
        val result = RootProcessRunner.run(RootProcessTestCommand.command("large-output"), 3000, 4096)
        assertEquals(0, result.code)
        assertEquals(4096, result.output.length)
    }
    @Test fun `failure status and diagnostic output survive`() {
        val result = RootProcessRunner.run(RootProcessTestCommand.command("failure"), 1000)
        assertEquals(4, result.code)
        assertTrue(result.output.contains("lock-unavailable"))
    }
}
