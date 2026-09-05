package com.v2ray.ang.ui.base

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.v2ray.ang.ui.compose.ToastType

/**
 * Screen state contract.
 *
 * Every screen owns exactly one immutable data class implementing this interface; it is the only
 * source the UI reads from. Loading is intentionally absent: it is held by [BaseViewModel] so that
 * no state class has to repeat the field.
 */
@Stable
interface BaseUiState

/**
 * User intent contract.
 *
 * The UI talks to the ViewModel only through `onAction(action)`; no other ViewModel member may be
 * called from composables.
 */
interface BaseAction

/**
 * One-time effect contract.
 *
 * Effects are never stored in [BaseUiState], so they are not replayed on recomposition or after a
 * configuration change.
 */
sealed interface BaseEvent {

    /** Show a toast/snackbar. The ViewModel describes it; the UI renders it. */
    @Immutable
    data class Message(val message: BaseMessage) : BaseEvent

    /** Close the current screen and hand [result] back to the caller. */
    @Immutable
    data class Finish(val result: BaseResult = BaseResult.Cancelled) : BaseEvent

    /** Navigate to [route]; the route itself knows how to build its Intent. */
    @Immutable
    data class Navigate(val route: BaseRoute) : BaseEvent

    /**
     * Capability only an Activity can perform (VPN permission, core start/stop, share, scan…).
     * Features extend it, e.g. `sealed interface MainEvent : BaseEvent.Platform`.
     */
    interface Platform : BaseEvent
}

/**
 * Navigation target.
 *
 * Each route builds its own [Intent], which removes string routes and the central `when` table
 * that used to live in the Activity. Returning `null` marks a non-Activity navigation (external
 * link) handled by the host.
 */
interface BaseRoute {
    fun intent(context: Context): Intent?
}

/**
 * Lazily resolved text: the ViewModel names a resource, the UI resolves it.
 * Keeps every Context out of the ViewModel and the data layer.
 */
sealed interface BaseText {

    /** Already-known plain text (server remarks, error body…). */
    @Immutable
    data class Raw(val value: String) : BaseText

    /** String resource plus optional format arguments. */
    @Immutable
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : BaseText

    companion object {
        fun of(value: CharSequence): BaseText = Raw(value.toString())
        fun of(@StringRes resId: Int, vararg args: Any): BaseText = Res(resId, args.toList())
    }
}

/** Payload of a toast/snackbar: what to say, how it looks, how long it stays. */
@Immutable
data class BaseMessage(
    val text: BaseText,
    val type: ToastType = ToastType.NORMAL,
    val long: Boolean = false
)

/**
 * Resolves this text with [context]. Arguments may themselves be [BaseText], so messages can be
 * composed, e.g. `BaseText.of(R.string.msg_updating, BaseText.of(R.string.title_sub_setting))`.
 */
fun BaseText.asString(context: Context): String = when (this) {
    is BaseText.Raw -> value
    is BaseText.Res ->
        if (args.isEmpty()) context.getString(resId)
        else context.getString(
            resId,
            *args.map { if (it is BaseText) it.asString(context) else it }.toTypedArray()
        )
}
