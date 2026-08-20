package com.v2ray.ang.service

import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RealPingExecutionLimiterTest {

    @Test
    fun customConfigMeasurementsAreSerializedAcrossWorkers() {
        runBlocking {
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)

            List(8) {
                async(Dispatchers.Default) {
                    RealPingExecutionLimiter.run(EConfigType.CUSTOM) {
                        val current = active.incrementAndGet()
                        maxActive.accumulateAndGet(current, ::maxOf)
                        Thread.sleep(20)
                        active.decrementAndGet()
                    }
                }
            }.awaitAll()

            assertEquals(1, maxActive.get())
        }
    }

    @Test
    fun generatedConfigMeasurementsRemainConcurrent() {
        runBlocking {
            val entered = CountDownLatch(2)
            val release = CountDownLatch(1)

            val jobs = List(2) {
                async(Dispatchers.Default) {
                    RealPingExecutionLimiter.run(EConfigType.VMESS) {
                        entered.countDown()
                        release.await(5, TimeUnit.SECONDS)
                    }
                }
            }

            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
            jobs.awaitAll()
        }
    }
}
