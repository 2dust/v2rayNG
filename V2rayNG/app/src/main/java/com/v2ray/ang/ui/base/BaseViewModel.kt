package com.v2ray.ang.ui.base

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseViewModel<S : BaseUiState, A : BaseAction>(
    initialState: S,
) : ViewModel() {

    // ---------- State (replayable, single source of truth) ----------

    private val _uiState = MutableStateFlow(initialState)

    /** The one state stream the UI collects. */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    /** Current snapshot, for reducers and guards. */
    protected val state: S get() = _uiState.value

    /** Atomically reduces the state; the only way to mutate it. */
    protected fun setState(reducer: S.() -> S) = _uiState.update(reducer)

    // ---------- Loading (reference counted, so nested jobs are safe) ----------

    private val loadingCount = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(false)

    /**
     * `true` while at least one `launch(loading = true)` job is running.
     */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private fun changeLoading(delta: Int) {
        val count = loadingCount.updateAndGet { (it + delta).coerceAtLeast(0) }
        _isLoading.value = count > 0
    }

    // ---------- One-time events (never replayed) ----------

    /**
     * UNLIMITED so events are never dropped; [receiveAsFlow] gives a single consumer that buffers
     * while the UI is STOPPED and delivers on resume.
     */
    private val _events = Channel<BaseEvent>(Channel.UNLIMITED)

    /** Effect stream consumed by [BaseScreen]. */
    val events: Flow<BaseEvent> = _events.receiveAsFlow()

    protected fun sendEvent(event: BaseEvent) {
        _events.trySend(event)
    }

    /** The single entry point for every user interaction. */
    abstract fun onAction(action: A)

    // ---------- Messages: describe, never render ----------

    protected fun toast(text: BaseText, type: ToastType = ToastType.NORMAL, long: Boolean = false) =
        sendEvent(BaseEvent.Message(BaseMessage(text, type, long)))

    protected fun toast(@StringRes resId: Int, vararg args: Any) = toast(BaseText.of(resId, *args))

    protected fun toast(message: CharSequence) = toast(BaseText.of(message))

    protected fun toastSuccess(@StringRes resId: Int = R.string.toast_success) =
        toast(BaseText.of(resId), ToastType.SUCCESS)

    protected fun toastSuccess(message: CharSequence) =
        toast(BaseText.of(message), ToastType.SUCCESS)

    protected fun toastError(@StringRes resId: Int = R.string.toast_failure) =
        toast(BaseText.of(resId), ToastType.ERROR)

    protected fun toastError(message: CharSequence) = toast(BaseText.of(message), ToastType.ERROR)

    protected fun toastInfo(@StringRes resId: Int, vararg args: Any) =
        toast(BaseText.of(resId, *args), ToastType.INFO)

    // ---------- Navigation / finishing / platform ----------

    protected fun navigate(route: BaseRoute) = sendEvent(BaseEvent.Navigate(route))

    protected fun finishWith(result: BaseResult = BaseResult.Cancelled) =
        sendEvent(BaseEvent.Finish(result))

    /** Delegates an Activity-only capability to the host. */
    protected fun platform(event: BaseEvent.Platform) = sendEvent(event)

    // ---------- Unified coroutine launcher ----------

    /**
     * Runs [block] in [viewModelScope] with optional loading accounting and central error handling.
     *
     * @param loading increments the loading counter for the duration of the job
     * @param onError invoked for non-cancellation failures; defaults to a generic error toast
     */
    protected fun launch(
        loading: Boolean = false,
        context: CoroutineContext = EmptyCoroutineContext,
        onError: (Throwable) -> Unit = { toastError() },
        block: suspend CoroutineScope.() -> Unit
    ): Job = viewModelScope.launch(context) {
        if (loading) changeLoading(+1)
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "${this@BaseViewModel.javaClass.simpleName} failed", e)
            onError(e)
        } finally {
            if (loading) changeLoading(-1)
        }
    }
}
