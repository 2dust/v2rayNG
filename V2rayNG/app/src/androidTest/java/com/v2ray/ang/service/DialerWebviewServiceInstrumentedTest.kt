package com.v2ray.ang.service

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class DialerWebviewServiceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dialer = DialerWebviewService()
    private val worker = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        dialer.stop()
        worker.shutdownNow()
        assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS))
    }

    @Test
    fun mainThreadStartCanBeStoppedAndRecreatedByTheHandoverWorker() {
        instrumentation.runOnMainSync { dialer.start(context, "127.0.0.1:10001") }
        val original = currentView()
        assertNotNull(original)

        worker.submit {
            dialer.stop()
            dialer.start(context, "127.0.0.1:10002")
        }.get(20, TimeUnit.SECONDS)

        val replacement = currentView()
        assertNotNull(replacement)
        assertNotSame(original, replacement)
        assertUsableOnMainThread(replacement!!)
        dialer.stop()
        dialer.stop()
        assertNull(currentView())
    }

    @Test
    fun workerCanCreateAWebViewThatIsOwnedByTheMainLooper() {
        worker.submit { dialer.start(context, "127.0.0.1:10001") }.get(20, TimeUnit.SECONDS)
        assertUsableOnMainThread(currentView()!!)
        instrumentation.runOnMainSync { dialer.stop() }
        assertNull(currentView())
    }

    @Test
    fun stopOvertakingAQueuedWorkerStartDoesNotRecreateTheWebView() {
        val version = lifecycleVersion()
        val previous = version.get()
        var start: Future<*>? = null
        instrumentation.runOnMainSync {
            start = worker.submit { dialer.start(context, "127.0.0.1:10001") }
            awaitNewRequest(version, previous)
            dialer.stop()
        }
        start!!.get(20, TimeUnit.SECONDS)
        assertNull(currentView())
    }

    @Test
    fun delayedStopCannotDestroyANewerMainThreadStart() {
        dialer.start(context, "127.0.0.1:10001")
        val original = currentView()
        val version = lifecycleVersion()
        val previous = version.get()
        var stop: Future<*>? = null
        instrumentation.runOnMainSync {
            stop = worker.submit { dialer.stop() }
            awaitNewRequest(version, previous)
            dialer.start(context, "127.0.0.1:10002")
        }
        stop!!.get(20, TimeUnit.SECONDS)
        val replacement = currentView()
        assertNotSame(original, replacement)
        assertUsableOnMainThread(replacement!!)
    }

    private fun assertUsableOnMainThread(view: WebView) {
        instrumentation.runOnMainSync {
            // These real framework calls enforce WebView's creating-thread contract.
            view.stopLoading()
            view.loadUrl("about:blank")
            view.onResume()
            view.resumeTimers()
        }
    }

    private fun currentView(): WebView? {
        var view: WebView? = null
        instrumentation.runOnMainSync {
            view = DialerWebviewService::class.java.getDeclaredField("webView").apply {
                isAccessible = true
            }.get(dialer) as WebView?
        }
        return view
    }

    private fun lifecycleVersion(): AtomicLong =
        DialerWebviewService::class.java.getDeclaredField("lifecycleVersion").apply {
            isAccessible = true
        }.get(dialer) as AtomicLong

    private fun awaitNewRequest(version: AtomicLong, previous: Long) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (version.get() == previous && System.nanoTime() < deadline) Thread.yield()
        assertTrue("Worker did not enter its lifecycle request", version.get() > previous)
    }
}
