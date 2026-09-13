package com.v2ray.ang.ui.compose

import android.app.Activity
import android.app.Application
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Main-thread registration makes host selection and message handoff one operation. */
@MainThread
object AppSnackbarManager {
    private val hosts = mutableListOf<(UserMessage) -> Unit>()

    internal fun register(host: (UserMessage) -> Unit) {
        hosts.add(host)
    }

    internal fun unregister(host: (UserMessage) -> Unit) {
        hosts.remove(host)
    }

    fun show(message: UserMessage): Boolean {
        val host = hosts.lastOrNull() ?: return false
        host(message)
        return true
    }
}

@Composable
private fun AppSnackbarBridge(viewModel: AppSnackbarViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val closeLabel = stringResource(R.string.action_close)

    LaunchedEffect(viewModel, lifecycleOwner, context, closeLabel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val showMessage: (UserMessage) -> Unit = { message ->
                viewModel.show(message, closeLabel)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!isOrderedBroadcast || resultCode == Activity.RESULT_OK) return
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
                    val what = intent.getIntExtra("key", 0)
                    val message = MessageHelper.serviceMessage(context, what) ?: return
                    showMessage(message)
                    NotificationHelper.cancelTransientMessage(context)
                    resultCode = Activity.RESULT_OK
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            AppSnackbarManager.register(showMessage)
            try {
                awaitCancellation()
            } finally {
                AppSnackbarManager.unregister(showMessage)
                context.unregisterReceiver(receiver)
            }
        }
    }
}

/** Activity ownership retains an undismissed error and its queue across configuration changes. */
class AppSnackbarViewModel(application: Application) : AndroidViewModel(application) {
    val hostState = SnackbarHostState()

    fun show(message: UserMessage, closeLabel: String) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var completed = false
            try {
                // Material queues messages and applies the accessibility timeout. A new
                // message never dismisses an error that the user is still reading.
                hostState.showSnackbar(
                    message = message.text,
                    actionLabel = closeLabel.takeIf { message.requiresDismissal },
                    duration = if (message.requiresDismissal) SnackbarDuration.Indefinite else SnackbarDuration.Short,
                )
                completed = true
            } finally {
                // Finishing the screen must not discard an error awaiting user dismissal.
                if (!completed && message.requiresDismissal) {
                    NotificationHelper.notifyTransientMessage(getApplication(), message)
                }
            }
        }
    }
}

@Composable
fun AppSnackbarHost() {
    val viewModel: AppSnackbarViewModel = viewModel()
    AppSnackbarBridge(viewModel)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
    ) {
        val maxTextHeight = maxHeight / 2
        SnackbarHost(
            hostState = viewModel.hostState,
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
