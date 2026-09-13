package com.v2ray.ang.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DialerWebviewServiceTest {
    @Test
    fun workerStartWaitsForMainThreadSetup() = Fixture().use { f ->
        val start = f.onWorker { f.dialer.start(f.context, "127.0.0.1:10001") }
        val queued = f.nextUpdate()
        assertFalse(start.isDone)
        assertTrue(f.views.isEmpty())
        queued.run()
        start.get(5, TimeUnit.SECONDS)
        verify(f.views.single()).loadUrl("http://127.0.0.1:10001/")
    }

    @Test
    fun workerStopCompletesCleanupBeforeReturning() = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        val view = f.views.single()
        val stop = f.onWorker { f.dialer.stop() }
        val queued = f.nextUpdate()
        assertFalse(stop.isDone)
        verify(view, never()).destroy()
        queued.run()
        stop.get(5, TimeUnit.SECONDS)
        verify(view).stopLoading()
        verify(view).pauseTimers()
        verify(view).onPause()
        verify(view).destroy()
    }

    @Test
    fun restartReplacesTheViewAndRepeatedStopDestroysItOnce() = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        f.dialer.start(f.context, "127.0.0.1:10002")
        verify(f.views.first()).destroy()
        verify(f.views.last()).loadUrl("http://127.0.0.1:10002/")
        f.dialer.stop()
        f.dialer.stop()
        f.views.forEach { verify(it, times(1)).destroy() }
    }

    @Test
    fun mainThreadStopInvalidatesAQueuedWorkerStart() = Fixture().use { f ->
        val start = f.onWorker { f.dialer.start(f.context, "127.0.0.1:10001") }
        val queued = f.nextUpdate()
        f.dialer.stop()
        queued.run()
        start.get(5, TimeUnit.SECONDS)
        assertTrue(f.views.isEmpty())
    }

    @Test
    fun interruptedWorkerCannotLeaveAQueuedStartBehind() = Fixture().use { f ->
        val start = f.onWorker { f.dialer.start(f.context, "127.0.0.1:10001") }
        val queued = f.nextUpdate()
        start.cancel(true)
        // The single worker reaches this marker only after handling the interruption.
        f.onWorker {}.get(5, TimeUnit.SECONDS)
        queued.run()
        assertTrue(f.views.isEmpty())
    }

    @Test
    fun rejectedMainThreadDispatchFailsWithoutWaiting() = Fixture().use { f ->
        whenever(f.handler.post(any())).thenReturn(false)
        val start = f.onWorker { f.dialer.start(f.context, "127.0.0.1:10001") }
        val thrown = assertThrows(ExecutionException::class.java) { start.get(5, TimeUnit.SECONDS) }
        assertTrue(thrown.cause is IllegalStateException)
        assertTrue(f.views.isEmpty())
    }

    @Test
    fun oldQueuedStopCannotDestroyANewerStart() = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        val stop = f.onWorker { f.dialer.stop() }
        val queued = f.nextUpdate()
        f.dialer.start(f.context, "127.0.0.1:10002")
        queued.run()
        stop.get(5, TimeUnit.SECONDS)
        verify(f.views.first()).destroy()
        verify(f.views.last(), never()).destroy()
    }

    @Test
    fun setupFailureCleansUpAndReachesTheWorkerUnwrapped() = Fixture().use { f ->
        val failure = IllegalStateException("WebView setup failed")
        f.loadFailure = failure
        val start = f.onWorker { f.dialer.start(f.context, "127.0.0.1:10001") }
        f.nextUpdate().run()
        val thrown = assertThrows(ExecutionException::class.java) { start.get(5, TimeUnit.SECONDS) }
        assertSame(failure, thrown.cause)
        verify(f.views.single()).destroy()
        f.dialer.stop()
        verify(f.views.single(), times(1)).destroy()
    }

    @Test
    fun cleanupFailureStillDestroysTheViewAndReachesTheCaller() = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        val view = f.views.single()
        val failure = IllegalStateException("WebView stop failed")
        doThrow(failure).whenever(view).stopLoading()
        assertSame(failure, assertThrows(IllegalStateException::class.java) { f.dialer.stop() })
        f.dialer.stop()
        verify(view, times(1)).destroy()
    }

    @Test
    fun callbacksCannotResumeADestroyedViewOrRescheduleKeepAlive(): Unit = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        val view = f.views.single()
        val client = argumentCaptor<WebViewClient>()
        verify(view).webViewClient = client.capture()
        val keepAlive = f.keepAlives.single()
        f.dialer.stop()
        clearInvocations(view, f.handler)
        client.firstValue.onPageFinished(view, "http://127.0.0.1:10001/")
        keepAlive.run()
        verify(view, never()).onResume()
        verify(view, never()).resumeTimers()
        verify(f.handler, never()).postDelayed(any(), any<Long>())
    }

    @Test
    fun emptyAddressStopsThePreviousViewWithoutCreatingAnother() = Fixture().use { f ->
        f.dialer.start(f.context, "127.0.0.1:10001")
        f.dialer.start(f.context, "")
        assertEquals(1, f.views.size)
        verify(f.views.single()).destroy()
    }

    private class Fixture : AutoCloseable {
        private val mainLooper = mock<Looper>()
        private val loopers = mockStatic(Looper::class.java).apply {
            `when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            `when`<Looper> { Looper.myLooper() }.thenReturn(mainLooper)
        }
        private val updates = LinkedBlockingQueue<Runnable>()
        private val worker = Executors.newSingleThreadExecutor()
        val keepAlives = mutableListOf<Runnable>()
        var loadFailure: Exception? = null
        val context = mock<Context>().also { whenever(it.applicationContext).thenReturn(it) }
        private val handlers = mockConstruction(Handler::class.java) { handler, _ ->
            whenever(handler.looper).thenReturn(mainLooper)
            whenever(handler.post(any())).thenAnswer {
                val task = it.getArgument<Runnable>(0)
                if (task is FutureTask<*>) updates.add(task) else keepAlives.add(task)
                true
            }
        }
        private val webViews = mockConstruction(WebView::class.java) { view, _ ->
            whenever(view.settings).thenReturn(mock<WebSettings>())
            loadFailure?.let { doThrow(it).whenever(view).loadUrl(any()) }
        }
        val dialer = DialerWebviewService()
        val handler get() = handlers.constructed().single()
        val views get() = webViews.constructed()

        fun onWorker(action: () -> Unit): Future<*> = worker.submit {
            // Static mocks are thread-local: this worker has no Android looper.
            mockStatic(Looper::class.java).use { action() }
        }

        fun nextUpdate(): Runnable = checkNotNull(updates.poll(5, TimeUnit.SECONDS)) {
            "Worker did not dispatch its WebView operation to the main looper"
        }

        override fun close() {
            worker.shutdownNow()
            worker.awaitTermination(5, TimeUnit.SECONDS)
            webViews.close()
            handlers.close()
            loopers.close()
        }
    }
}
