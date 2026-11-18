package com.v2ray.ang.ui.update

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.handler.AppUpdateManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** What the update sheet shows. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    /** A newer release exists; the user can update now or later. */
    data class Available(val result: CheckUpdateResult) : UpdateUiState

    /** [total] is -1 while the size is unknown. */
    data class Downloading(val result: CheckUpdateResult, val downloaded: Long, val total: Long) : UpdateUiState

    /** Downloaded; waiting for the install permission or the system installer. */
    data class ReadyToInstall(val result: CheckUpdateResult, val file: File, val savedTo: String?) : UpdateUiState

    /** The user did not allow installing: the file is in Downloads for a manual install. */
    data class SavedForManualInstall(val location: String) : UpdateUiState
}

/**
 * State machine of the in-app update, shared by the automatic announcement on the main screen and
 * the "Check for update" screen. The Compose host ([AppUpdateSheetHost]) renders the state and does
 * the parts that need an Activity (permission screen, installer).
 *
 * @param rememberPostpone When true, "Later" is remembered so the same version is not announced again.
 */
class AppUpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rememberPostpone: Boolean,
    private val onError: (Int) -> Unit
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** Shows the sheet for [result] unless another update step is already on screen. */
    fun offer(result: CheckUpdateResult) {
        if (_state.value is UpdateUiState.Idle) {
            _state.value = UpdateUiState.Available(result)
        }
    }

    /** "Later" or swiping the sheet away. */
    fun later() {
        val available = _state.value as? UpdateUiState.Available ?: return
        if (rememberPostpone) {
            AppUpdateManager.postpone(available.result.latestVersion.orEmpty())
        }
        _state.value = UpdateUiState.Idle
    }

    fun startDownload() {
        val result = (_state.value as? UpdateUiState.Available)?.result ?: return
        _state.value = UpdateUiState.Downloading(result, 0, result.assetSize.takeIf { it > 0 } ?: -1)
        downloadJob = scope.launch {
            try {
                val file = AppUpdateManager.download(context, result) { downloaded, total ->
                    _state.value = UpdateUiState.Downloading(
                        result,
                        downloaded,
                        total.takeIf { it > 0 } ?: result.assetSize.takeIf { it > 0 } ?: -1
                    )
                }
                val savedTo = AppUpdateManager.saveToDownloads(context, file)
                _state.value = UpdateUiState.ReadyToInstall(result, file, savedTo)
            } catch (e: CancellationException) {
                // cancelDownload() already put the state back
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to download the update", e)
                onError(R.string.update_download_failed)
                _state.value = UpdateUiState.Available(result)
            }
        }
    }

    fun cancelDownload() {
        val downloading = _state.value as? UpdateUiState.Downloading ?: return
        downloadJob?.cancel()
        _state.value = UpdateUiState.Available(downloading.result)
    }

    /** The installer was started (or there is nothing left to do): close the sheet. */
    fun finish() {
        _state.value = UpdateUiState.Idle
    }

    /** The user did not allow installing; tell them where the file is. */
    fun installBlocked(location: String) {
        _state.value = UpdateUiState.SavedForManualInstall(location)
    }
}
