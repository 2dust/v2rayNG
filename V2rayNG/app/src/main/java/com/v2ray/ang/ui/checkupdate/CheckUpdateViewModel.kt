package com.v2ray.ang.ui.checkupdate

import com.v2ray.ang.R
import com.v2ray.ang.repository.CheckUpdateRepository
import com.v2ray.ang.ui.AppRoute
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CheckUpdateViewModel(
    private val repo: CheckUpdateRepository
) : BaseViewModel<CheckUpdateUiState, CheckUpdateAction>(CheckUpdateUiState()) {

    private var checkJob: Job? = null
    private var preReleaseJob: Job? = null

    init {
        val preRelease = repo.isCheckPreRelease()
        setState { copy(checkPreRelease = preRelease, versionText = repo.appVersionText()) }

        launch(onError = { /* the version line keeps the short form; launch already logged it */ }) {
            val text = repo.fullVersionText()
            setState { copy(versionText = text) }
        }

        check(preRelease)
    }

    override fun onAction(action: CheckUpdateAction) {
        when (action) {
            CheckUpdateAction.Back -> finishWith(BaseResult.Cancelled)
            is CheckUpdateAction.TogglePreRelease -> togglePreRelease(action.enabled)
            CheckUpdateAction.CheckNow -> check(state.checkPreRelease)
            is CheckUpdateAction.DownloadConfirmed -> download(action.url)
        }
    }

    override fun onCleared() {
        checkJob?.cancel()
        preReleaseJob?.cancel()
        super.onCleared()
    }

    private fun togglePreRelease(enabled: Boolean) {
        setState { copy(checkPreRelease = enabled) }
        val previous = preReleaseJob
        preReleaseJob = launch(onError = { toastError() }) {
            previous?.join()
            val persisted = withContext(NonCancellable) { repo.setCheckPreRelease(enabled) }
            setState { copy(checkPreRelease = persisted) }
        }
    }

    private fun download(url: String) {
        if (url.isEmpty()) toastError() else navigate(AppRoute.OpenUrl(url))
    }

    private fun check(includePreRelease: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob = launch(loading = true) {
            toast(R.string.update_checking_for_update)
            val result = repo.checkForUpdate(includePreRelease)
            val url = result.downloadUrl
            val error = result.error
            when {
                result.hasUpdate && !url.isNullOrEmpty() -> platform(
                    CheckUpdateEvent.UpdateAvailable(
                        UpdateInfo(
                            version = result.latestVersion.orEmpty(),
                            releaseNotes = result.releaseNotes.orEmpty(),
                            downloadUrl = url
                        )
                    )
                )
                !error.isNullOrEmpty() -> toastError(error)
                else -> toastSuccess(R.string.update_already_latest_version)
            }
        }
    }
}
