package com.v2ray.ang.ui.backup

import android.net.Uri
import com.v2ray.ang.R
import com.v2ray.ang.repository.BackupRepository
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseText
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ToastType
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

class BackupViewModel(
    private val repo: BackupRepository
) : BaseViewModel<BackupUiState, BackupAction>(BackupUiState()) {

    /** File transfers are refused while one is running: two of them would race over the cache. */
    private var transferJob: Job? = null

    /** Settings writes are chained instead of refused */
    private var saveJob: Job? = null

    /** Not part of UiState: nothing renders it, it only decides the result handed back on exit. */
    private var restored = false

    private val busy: Boolean get() = transferJob?.isActive == true

    init {
        launch(onError = {}) {
            repo.cleanWorkDir()
            val form = repo.loadWebDav().toForm()
            setState { copy(webDav = form, draft = form) }
        }
    }

    override fun onAction(action: BackupAction) {
        when (action) {
            BackupAction.Back -> exit()

            BackupAction.BackupClicked -> openChannelPicker(restoring = false)
            BackupAction.RestoreClicked -> openChannelPicker(restoring = true)
            BackupAction.ShareClicked -> share()

            BackupAction.WebDavClicked -> openWebDavEditor()
            BackupAction.WebDavSaved -> saveWebDav()

            BackupAction.CleanupClicked -> openCleanupConfirmation()
            BackupAction.CleanupConfirmed -> cleanProfiles()

            is BackupAction.ChannelSelected -> onChannelSelected(action.channel)

            is BackupAction.WebDavFieldChanged -> setState {
                copy(draft = draft.updated(action.field, action.value))
            }

            is BackupAction.ExportUriSelected -> action.uri?.let(::exportTo)
            is BackupAction.ImportUriSelected -> action.uri?.let(::restoreFrom)
            is BackupAction.ShareResult -> onShareResult(action.path, action.ok)
        }
    }

    // ===== exit =====

    private fun exit() {
        if (busy) return toastInfo(R.string.msg_dialog_progress)
        launch(onError = {}) {
            // A confirmed WebDAV save must reach the disk even if the user leaves immediately.
            saveJob?.join()
            finishWith(
                if (restored) {
                    BaseResult.Changed(restartService = true, refreshList = true)
                } else {
                    BaseResult.Cancelled
                }
            )
        }
    }

    // ===== dialogs =====

    private fun openChannelPicker(restoring: Boolean) {
        if (busy) return toastInfo(R.string.msg_dialog_progress)
        platform(BackupEvent.ShowChannelPicker(restoring))
    }

    private fun openWebDavEditor() {
        setState { copy(draft = webDav) }
        platform(BackupEvent.ShowWebDavEditor)
    }

    private fun openCleanupConfirmation() {
        if (busy) return toastInfo(R.string.msg_dialog_progress)
        platform(BackupEvent.ShowCleanupConfirmation)
    }

    // ===== channel dispatch =====

    private fun onChannelSelected(channel: BackupChannel) = when (channel) {
        BackupChannel.LOCAL -> if (pendingRestore) {
            platform(BackupEvent.PickFile)
        } else {
            transfer { platform(BackupEvent.CreateDocument(repo.defaultFileName())) }
        }

        BackupChannel.WEBDAV -> if (pendingRestore) restoreViaWebDav() else backupViaWebDav()
    }

    /** Set by [openChannelPicker] through the event payload the screen echoes back. */
    private var pendingRestore = false

    // ===== WebDAV settings =====

    private fun saveWebDav() {
        val form = state.draft
        val previous = saveJob
        saveJob = launch {
            previous?.join()
            withContext(NonCancellable) { repo.saveWebDav(form.toConfig()) }
            setState { copy(webDav = form) }
            toastSuccess()
        }
    }

    private fun requireWebDav(): WebDavForm? {
        val form = state.webDav
        if (!form.configured) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return null
        }
        return form
    }

    // ===== backup =====

    private fun exportTo(uri: Uri) = transfer {
        val zip = repo.packToCache() ?: return@transfer toastError()
        try {
            if (repo.exportTo(zip, uri)) toastSuccess() else toastError()
        } finally {
            repo.discard(zip)
        }
    }

    private fun share() = transfer {
        val zip = repo.packToCache() ?: return@transfer toastError()
        platform(BackupEvent.ShareFile(zip.absolutePath))
    }

    private fun onShareResult(path: String, ok: Boolean) {
        if (!ok) toastError()
        launch(onError = {}) { repo.discard(File(path)) }
    }

    private fun backupViaWebDav() {
        val form = requireWebDav() ?: return
        transfer {
            val zip = repo.packToCache() ?: return@transfer toastError()
            try {
                if (repo.uploadBackup(form.toConfig(), zip)) toastSuccess() else toastError()
            } finally {
                repo.discard(zip)
            }
        }
    }

    // ===== restore =====

    private fun restoreFrom(uri: Uri) = transfer {
        val zip = repo.importToCache(uri) ?: return@transfer toastError()
        applyRestore(zip)
    }

    private fun restoreViaWebDav() {
        val form = requireWebDav() ?: return
        transfer {
            val zip = repo.downloadBackup(form.toConfig()) ?: return@transfer toastError()
            applyRestore(zip)
        }
    }

    private suspend fun applyRestore(zip: File) {
        val ok = try {
            repo.restore(zip)
        } finally {
            repo.discard(zip)
        }
        if (!ok) return toastError()
        restored = true
        val form = repo.loadWebDav().toForm()
        setState { copy(webDav = form, draft = form) }
        toastSuccess()
    }

    // ===== cleanup =====

    /** Failures propagate to `launch`'s onError, which logs through LogUtil and toasts once. */
    private fun cleanProfiles() = launch(loading = true) {
        val removed = repo.cleanupProfiles()
        if (removed == null) {
            toastError(R.string.toast_profile_storage_cleanup_skipped)
        } else {
            toast(BaseText.of(R.string.toast_profile_storage_cleanup, removed), ToastType.SUCCESS)
        }
    }

    // ===== plumbing =====

    private fun transfer(block: suspend () -> Unit) {
        if (busy) return toastInfo(R.string.msg_dialog_progress)
        transferJob = launch(loading = true) { block() }
    }

    override fun onCleared() {
        transferJob = null
        saveJob = null
        super.onCleared()
    }
}
