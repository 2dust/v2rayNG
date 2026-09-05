package com.v2ray.ang.ui.base

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.compose.LocalAppSnackbar
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.Flow

/** Walks ContextWrapper chain to find the hosting Activity. */
fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

/**
 * Universal screen wrapper.
 *
 * Provides [Scaffold] with slots, collects [uiState], and handles navigation/events.
 *
 * @param onEvent Optional interceptor for events; return `true` to consume.
 * @param onResult Callback for [BaseResult] from child screens.
 */
@Composable
fun <S : BaseUiState, A : BaseAction> BaseScreen(
    viewModel: BaseViewModel<S, A>,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets(0),
    autoToastResult: Boolean = true,
    onEvent: (BaseEvent) -> Boolean = { false },
    onResult: (BaseResult) -> Unit = {},
    content: @Composable (state: S, onAction: (A) -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val okText = stringResource(R.string.toast_success)

    val resultHandler by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(BaseResultContract()) { result ->
        if (autoToastResult && result.notify) snackbar.show(okText, ToastType.SUCCESS)
        resultHandler(result)
    }

    BaseEventEffect(
        events = viewModel.events,
        onEvent = { event ->
            when {
                onEvent(event) -> true
                event is BaseEvent.Navigate -> when (val route = event.route) {
                    is AppRoute.OpenUrl -> {
                        Utils.openUri(context, route.url)
                        true
                    }
                    else -> route.intent(context)?.let { launcher.launch(it); true } ?: false
                }
                else -> false
            }
        },
    )

    val dispatch = remember(viewModel) { viewModel::onAction }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(state, dispatch)
        }
    }
}

/**
 * Lifecycle-aware consumer of one-time [BaseEvent]s.
 *
 * Events are collected while the lifecycle is at least STARTED; buffered events are delivered on
 * resume.
 */
@Composable
fun BaseEventEffect(
    events: Flow<BaseEvent>,
    onEvent: (BaseEvent) -> Boolean = { false },
) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEvent)

    LaunchedEffect(events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                if (handler(event)) return@collect
                when (event) {
                    is BaseEvent.Message -> snackbar.show(
                        message = event.message.text.asString(context),
                        type = event.message.type,
                        long = event.message.long,
                    )
                    is BaseEvent.Finish -> context.findActivity()?.finishWithResult(event.result)
                    // Navigate is intercepted by BaseScreen; Platform is handled by host Activity.
                    is BaseEvent.Navigate, is BaseEvent.Platform -> Unit
                }
            }
        }
    }
}
