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
import java.util.concurrent.TimeUnit

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

}
