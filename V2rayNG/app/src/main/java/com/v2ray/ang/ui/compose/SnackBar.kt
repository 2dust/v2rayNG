package com.v2ray.ang.ui.compose

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.LocalActivity
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.UUID

/** Process ownership lets unread messages follow navigation instead of dying with an activity. */
@MainThread
class AppSnackbarManager(
    private val scope: CoroutineScope,
    private val notifyBackground: (UserMessage) -> Unit,
    private val clearBackground: () -> Unit,
    private val closeLabel: () -> String,
) {
    val hostState = SnackbarHostState()
    private val hosts = mutableListOf<String>()
    private val _activeHost = MutableStateFlow<String?>(null)
    val activeHost = _activeHost.asStateFlow()
    private val pending = linkedMapOf<Job, UserMessage>()
    private var presentation: Pair<String, SnackbarData>? = null
    var isForeground = false
        private set

    internal fun register(host: String) {
        presentation?.let { (previousHost, snackbar) ->
            // Do not replay already-shown transient feedback on a different screen.
            // Unseen queued messages and errors requiring dismissal still follow navigation.
            if (previousHost != host && snackbar.visuals.duration != SnackbarDuration.Indefinite) {
                snackbar.dismiss()
                presentation = null
            }
        }
        hosts.add(host)
        _activeHost.value = host
    }

    internal fun unregister(host: String) {
        hosts.remove(host)
        _activeHost.value = hosts.lastOrNull()
    }

    internal fun onPresented(host: String, snackbar: SnackbarData) {
        if (_activeHost.value == host && hostState.currentSnackbarData === snackbar) {
            presentation = host to snackbar
        }
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
                AppSnackbarVisuals(message, if (message.requiresDismissal) closeLabel() else null),
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
                    val details = if (what == AppConfig.MSG_STATE_START_FAILURE) intent.getStringExtra("content") else null
                    val message = MessageHelper.serviceMessage(context, what, details) ?: return
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

internal data class AppSnackbarVisuals(
    val feedback: UserMessage,
    override val actionLabel: String?,
) : SnackbarVisuals {
    override val message = feedback.text
    override val duration = when {
        feedback.requiresDismissal -> SnackbarDuration.Indefinite
        feedback.long -> SnackbarDuration.Long
        else -> SnackbarDuration.Short
    }
    override val withDismissAction = false
}

@Composable
fun AppSnackbarHost() {
    val manager = (LocalContext.current.applicationContext as AngApplication).snackbarManager
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // A recreated screen is the same destination, not navigation to a new message host.
    val host = rememberSaveable { UUID.randomUUID().toString() }
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
    if (activeHost != host) return
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            // Keep upstream's offset above the service controls, but leave room above the IME.
            .padding(bottom = if (imeVisible) 8.dp else 100.dp),
    ) {
        val maxTextHeight = maxHeight / 2
        SnackbarHost(
            hostState = manager.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = maxWidth * 0.75f),
        ) { data ->
            SideEffect {
                // A save-and-finish can still compose one last frame; leave its feedback unread.
                if (activity?.isFinishing != true) manager.onPresented(host, data)
            }
            val type = (data.visuals as AppSnackbarVisuals).feedback.type
            val background = when (type) {
                UserMessage.Type.NORMAL -> if (LocalDarkTheme.current) toastNormalBgDark else toastNormalBgLight
                UserMessage.Type.SUCCESS -> toastSuccessBg
                UserMessage.Type.ERROR -> toastErrorBg
                UserMessage.Type.INFO -> toastInfoBg
            }
            Surface(
                modifier = Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(24.dp),
                color = background,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = data.visuals.message,
                        color = toastTextColor,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false)
                            .heightIn(max = maxTextHeight)
                            .verticalScroll(rememberScrollState()),
                    )
                    data.visuals.actionLabel?.let { label ->
                        TextButton(
                            onClick = { data.performAction() },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.textButtonColors(contentColor = toastTextColor),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}
