package com.v2ray.ang.ui.urlscheme

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.delay
import com.v2ray.ang.repository.UrlSchemeImport
import com.v2ray.ang.repository.UrlSchemeRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

class UrlSchemeViewModel(
    private val handle: SavedStateHandle,
    private val repo: UrlSchemeRepository
) : BaseViewModel<UrlSchemeUiState, UrlSchemeAction>(UrlSchemeUiState) {

    /**
     * Claim on the import. `compareAndSet` makes "is anyone running / I am running now" one
     * indivisible step; a plain flag left a window between the test and the assignment.
     */
    private val inFlight = AtomicBoolean(false)

    /** The pending hand-over to the main screen. A newer Intent supersedes it. */
    private var leaveJob: Job? = null

    override fun onAction(action: UrlSchemeAction) {
        when (action) {
            is UrlSchemeAction.IntentReceived -> receive(action.request, action.fresh)
        }
    }

    private fun receive(request: UrlSchemeRequest, fresh: Boolean) {
        if (!fresh && request.signature == handle.get<String>(KEY_DONE)) {
            LogUtil.i(AppConfig.TAG, "UrlScheme: replayed intent ignored (${request.source})")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            LogUtil.i(AppConfig.TAG, "UrlScheme: dropped ${request.source}, import running")
            return
        }
        // Reached only by a request that will be handled, so it may take over the dwell window of
        // the previous one instead of racing it to navigate and finish twice.
        leaveJob?.cancel()

        LogUtil.i(AppConfig.TAG, "UrlScheme: handling ${request.source}")
        if (request.source == UrlSchemeSource.UNSUPPORTED) {
            complete(request, R.string.toast_action_not_allowed, ok = false)
            return
        }
        importRequest(request)
    }

    private fun importRequest(request: UrlSchemeRequest) = launch(
        loading = true,
        onError = { complete(request, R.string.import_subscription_failure, ok = false) }
    ) {
        val outcome = repo.import(
            payload = request.payload,
            fragment = request.fragment,
            // A share sheet hands over literal text; only an external link can arrive
            // double-encoded, so only it may be decoded a second time.
            allowDoubleDecode = request.source != UrlSchemeSource.SHARE
        )
        when {
            outcome !is UrlSchemeImport.Done ->
                complete(request, R.string.toast_invalid_url, ok = false)

            outcome.isEmpty ->
                complete(request, R.string.import_subscription_failure, ok = false)

            else -> complete(
                request = request,
                message = if (outcome.subscriptionCount > 0) R.string.import_subscription_success
                else R.string.toast_success,
                ok = true
            )
        }
    }

    /**
     * Marks the request handled, reports the outcome and schedules the hand-over.
     *
     * The signature is persisted here and not when the import starts: if the process dies halfway
     * through, nothing was completed and the user's link deserves a second chance on restore.
     */
    private fun complete(request: UrlSchemeRequest, @StringRes message: Int, ok: Boolean) {
        inFlight.set(false)
        handle[KEY_DONE] = request.signature

        if (ok) toastSuccess(message) else toastError(message)
        leave(
            result = if (ok) BaseResult.Changed(refreshList = true) else BaseResult.Cancelled,
            dwellMs = if (ok) DWELL_SUCCESS_MS else DWELL_ERROR_MS
        )
    }

    /**
     * Holds the screen open long enough for the message to be read, then hands over.
     *
     * Stored in a field because it is replaceable: an Intent arriving inside the dwell window
     * cancels this one, so the app never navigates or finishes twice.
     */
    private fun leave(result: BaseResult, dwellMs: Long) {
        leaveJob?.cancel()
        leaveJob = launch {
            delay(dwellMs)
            navigate(AppRoute.Main)
            finishWith(result)
        }
    }

    private companion object {
        const val KEY_DONE = "url_scheme_completed_signature"
        const val DWELL_SUCCESS_MS = 700L
        const val DWELL_ERROR_MS = 1800L
    }
}
