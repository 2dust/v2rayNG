package com.v2ray.ang.ui.compose

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Process ownership lets unread messages follow navigation instead of dying with an activity. */
@MainThread
class AppSnackbarManager(
    private val scope: CoroutineScope,
    private val notifyBackground: (UserMessage) -> Unit,
    private val clearBackground: () -> Unit,
    private val closeLabel: () -> String,
) {
    val hostState = SnackbarHostState()
    private val hosts = mutableListOf<Any>()
    private val _activeHost = MutableStateFlow<Any?>(null)
    val activeHost = _activeHost.asStateFlow()
    private val pending = linkedMapOf<Job, UserMessage>()
    var isForeground = false
        private set

    internal fun register(host: Any) {
        hosts.add(host)
        _activeHost.value = host
    }

    internal fun unregister(host: Any) {
        hosts.remove(host)
        _activeHost.value = hosts.lastOrNull()
    }

    internal fun setForeground(foreground: Boolean) {
        isForeground = foreground
        if (!foreground) {
            // Cancel the Material queue before posting: nothing may expire in a hidden host.
            pending.toList().forEach { (job, message) ->
                job.cancel()
                notifyBackground(message)
            }
        }
    }

    fun show(message: UserMessage) {
        if (message.text.isBlank()) return
        if (!isForeground) {
            notifyBackground(message)
            return
        }
        // Material still owns serialization, dismissal and the accessibility-adjusted timeout.
        // Track its jobs only so unread messages can be handed to background notifications.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            hostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.requiresDismissal) closeLabel() else null,
                duration = if (message.requiresDismissal) SnackbarDuration.Indefinite else SnackbarDuration.Short,
            )
        }
        pending[job] = message
        job.invokeOnCompletion { pending.remove(job) }
        job.start()
        clearBackground()
    }

    companion object {
        fun create(context: Context): AppSnackbarManager {
            val appContext = context.applicationContext
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val manager = AppSnackbarManager(
                scope = ProcessLifecycleOwner.get().lifecycleScope,
                notifyBackground = { NotificationHelper.notifyTransientMessage(appContext, it) },
                clearBackground = { NotificationHelper.cancelTransientMessage(appContext) },
                closeLabel = { AppLocaleManager.localizedContext(appContext).getString(R.string.action_close) },
            )
            // ProcessLifecycleOwner suppresses stop/start churn during activity replacement.
            // The observer and receiver have the same lifetime as this process-owned manager.
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = manager.setForeground(true)
                override fun onStop(owner: LifecycleOwner) = manager.setForeground(false)
            })
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!isOrderedBroadcast || resultCode == Activity.RESULT_OK) return
                    if (!manager.isForeground) return
                    val what = intent.getIntExtra("key", 0)
                    val message = MessageHelper.serviceMessage(context, what) ?: return
                    manager.show(message)
                    // The process now owns delivery even if the current screen closes.
                    resultCode = Activity.RESULT_OK
                }
            }
            ContextCompat.registerReceiver(
                appContext, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            return manager
        }
    }
}

@Composable
fun AppSnackbarHost() {
    val manager = (LocalContext.current.applicationContext as AngApplication).snackbarManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val host = remember { Any() }
    LaunchedEffect(manager, lifecycleOwner, host) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            manager.register(host)
            try {
                awaitCancellation()
            } finally {
                manager.unregister(host)
            }
        }
    }
    val activeHost by manager.activeHost.collectAsStateWithLifecycle()
    if (activeHost !== host) return
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        val maxTextHeight = maxHeight / 2
        SnackbarHost(
            hostState = manager.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Keep both sides clear of MainBottomBar's 56 dp button and 24 dp edge inset.
                .padding(horizontal = 80.dp)
                .widthIn(max = 600.dp),
        ) { data ->
            Snackbar(
                modifier = Modifier.padding(12.dp),
                actionOnNewLine = data.visuals.actionLabel != null,
                action = data.visuals.actionLabel?.let { label ->
                    {
                        TextButton(
                            onClick = { data.performAction() },
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                        ) { Text(label) }
                    }
                },
            ) {
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.heightIn(max = maxTextHeight).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
