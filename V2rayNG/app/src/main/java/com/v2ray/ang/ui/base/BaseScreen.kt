package com.v2ray.ang.ui.base

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.delay
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
 * A blocking overlay that appears for two frames is worse than no overlay: it reads as a stutter.
 * Anything finishing inside this window renders silently.
 */
private const val LoadingOverlayDelayMs = 200L

@Composable
private fun rememberDelayedFlag(active: Boolean, delayMs: Long): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            visible = false
        } else {
            delay(delayMs)
            visible = true
        }
    }
    return visible
}

/**
 * Universal screen wrapper.
 *
 * Provides [Scaffold] with slots, collects [uiState] and [isLoading], displays a loading overlay
 * (delayed by 200ms to avoid flashing on fast operations), and handles navigation/events.
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
    showLoading: Boolean = true,
    autoToastResult: Boolean = true,
    onEvent: (BaseEvent) -> Boolean = { false },
    onResult: (BaseResult) -> Unit = {},
    content: @Composable (state: S, onAction: (A) -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = if (showLoading) {
        viewModel.isLoading.collectAsStateWithLifecycle().value
    } else {
        false
    }
    val showOverlay = rememberDelayedFlag(isLoading, LoadingOverlayDelayMs)

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

    // Cache dispatcher to allow content composable to be skippable.
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
            if (showOverlay) BaseLoading()
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

/** Full-screen loading overlay with a translucent scrim and centred spinner. */
@Composable
fun BaseLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
