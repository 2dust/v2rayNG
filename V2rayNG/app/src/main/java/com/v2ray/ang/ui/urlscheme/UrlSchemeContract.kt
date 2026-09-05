package com.v2ray.ang.ui.urlscheme

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseUiState

/** `v2rayng://install-config?url=...` */
private const val HOST_INSTALL_CONFIG = "install-config"

/** `v2rayng://install-sub?url=...` */
private const val HOST_INSTALL_SUB = "install-sub"

/** Which external entry point produced the request. */
enum class UrlSchemeSource {
    /** `ACTION_SEND` — a share sheet. The payload is literal text, never percent-encoded. */
    SHARE,

    /** `v2rayng://install-config?url=...` */
    INSTALL_CONFIG,

    /** `v2rayng://install-sub?url=...` */
    INSTALL_SUB,

    /** Anything else; reported to the user and dropped. */
    UNSUPPORTED
}

/**
 * Maps a scheme host onto its entry point.
 *
 * Kept here, pure and Intent-free, so the only decision the deep link involves is unit-testable
 * without Robolectric; the Activity is left with nothing but reading fields off an Intent.
 */
internal fun urlSchemeSourceOf(host: String?): UrlSchemeSource = when (host) {
    HOST_INSTALL_CONFIG -> UrlSchemeSource.INSTALL_CONFIG
    HOST_INSTALL_SUB -> UrlSchemeSource.INSTALL_SUB
    else -> UrlSchemeSource.UNSUPPORTED
}

/**
 * The Intent reduced to plain, serialisable data.
 *
 * The Activity performs this translation because only it may touch an Intent; from here on the
 * request is an ordinary value.
 */
@Immutable
data class UrlSchemeRequest(
    val source: UrlSchemeSource,
    val payload: String = "",
    /** Scheme-level fragment, used as the default group name. Only external links carry one. */
    val fragment: String = ""
) {
    /**
     * Bounded identity of this request, used to import an Intent exactly once.
     *
     * Deliberately a digest and not the payload itself: this value is persisted into
     * `SavedStateHandle`, and a shared subscription blob can be megabytes — putting it in the
     * saved-state Bundle risks TransactionTooLargeException on every configuration change.
     * A hash collision would only skip a duplicate import, which is the safe direction.
     */
    val signature: String
        get() = "${source.name}:${payload.length}:${payload.hashCode()}:${fragment.hashCode()}"
}

@Immutable
data object UrlSchemeUiState : BaseUiState

/** The complete set of intents; the Activity is the only caller. */
sealed interface UrlSchemeAction : BaseAction {

    /**
     * @param fresh `true` for `onNewIntent`, which legitimately re-runs an identical payload and
     *              therefore bypasses the once-only guard
     */
    data class IntentReceived(
        val request: UrlSchemeRequest,
        val fresh: Boolean = false
    ) : UrlSchemeAction
}
