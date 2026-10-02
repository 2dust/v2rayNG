package com.v2ray.ang.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.v2ray.ang.contracts.IDialerService
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicLong

class DialerWebviewService : IDialerService {
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleVersion = AtomicLong()
    private val keepAliveInterval = 30_000L // 30 seconds

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            val view = webView ?: return
            view.resumeTimers()
            view.onResume()
            handler.postDelayed(this, keepAliveInterval)
        }
    }


    /**
     * Starts the WebView.
     * @param context Service context
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun start(context: Context, dialerAddr: String) = updateOnMainThread {
        stopOnMainThread()
        if (dialerAddr.isEmpty()) return@updateOnMainThread
        val dialerUrl = "http://$dialerAddr/"

        val view = WebView(context.applicationContext)
        webView = view
        try {
            view.apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Allow JS to run even if not triggered by user
                    mediaPlaybackRequiresUserGesture = false
                    // Prevent aggressive caching issues
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view === webView) {
                            view?.onResume()
                            view?.resumeTimers()
                        }
                    }
                }

                loadUrl(dialerUrl)
            }
            handler.post(keepAliveRunnable)
        } catch (e: Exception) {
            try {
                stopOnMainThread()
            } catch (cleanupError: Exception) {
                e.addSuppressed(cleanupError)
            }
            throw e
        }
    }

    override fun stop() = updateOnMainThread { stopOnMainThread() }

    private fun stopOnMainThread() {
        handler.removeCallbacks(keepAliveRunnable)
        val view = webView ?: return
        webView = null
        view.apply {
            try {
                stopLoading()
                pauseTimers()
                // Important to call onPause to stop internal Chromium threads properly
                onPause()
            } finally {
                destroy()
            }
        }
    }

    private fun updateOnMainThread(action: () -> Unit) {
        val version = lifecycleVersion.incrementAndGet()
        val update = {
            // A stop or replacement can overtake a worker's queued start on the main thread.
            if (lifecycleVersion.get() == version) action()
        }
        if (Looper.myLooper() == handler.looper) {
            update()
            return
        }

        // WebView requires its creating looper on all Android versions. Keep the dialer's
        // synchronous contract so reload waits for cleanup/setup and receives any failure.
        val task = FutureTask(update)
        check(handler.post(task)) { "WebView looper is shutting down" }
        try {
            task.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: InterruptedException) {
            task.cancel(false)
            Thread.currentThread().interrupt()
            throw e
        }
    }

}
