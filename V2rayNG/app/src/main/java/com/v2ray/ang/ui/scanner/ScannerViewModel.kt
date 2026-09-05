package com.v2ray.ang.ui.scanner

import android.net.Uri
import com.v2ray.ang.R
import com.v2ray.ang.repository.ScannerRepository
import com.v2ray.ang.ui.base.BaseResult
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.Job

class ScannerViewModel(
    private val repo: ScannerRepository
) : BaseViewModel<ScannerUiState, ScannerAction>(ScannerUiState()) {

    /** Replaceable: picking a second image must cancel the decode of the first one. */
    private var decodeJob: Job? = null

    init {
        // Entering the screen means "scan now"; the permission answer arrives as an Action.
        platform(ScannerEvent.RequestCameraPermission)
    }

    override fun onAction(action: ScannerAction) {
        when (action) {
            ScannerAction.Back -> finishWith(BaseResult.Cancelled)

            ScannerAction.StartScanClicked -> platform(ScannerEvent.RequestCameraPermission)

            ScannerAction.CameraPermissionGranted -> setState { copy(scanning = true) }

            ScannerAction.StopScanClicked -> setState {
                copy(scanning = false, hasTorch = false, torchEnabled = false)
            }

            is ScannerAction.CameraReady -> setState {
                copy(hasTorch = action.hasTorch, torchEnabled = torchEnabled && action.hasTorch)
            }

            ScannerAction.CameraFailed -> onCameraFailed()

            ScannerAction.ToggleTorch -> setState {
                if (hasTorch) copy(torchEnabled = !torchEnabled) else this
            }

            ScannerAction.PickImageClicked -> platform(ScannerEvent.PickImage)

            is ScannerAction.ImageSelected -> action.uri?.let(::decodeImage)

            // The analyzer may emit once more while the screen is closing; ignore it.
            is ScannerAction.Decoded -> if (state.scanning) succeed(action.text)
        }
    }

    private fun onCameraFailed() {
        setState { copy(scanning = false, hasTorch = false, torchEnabled = false) }
        toastError()
    }

    private fun decodeImage(uri: Uri) {
        decodeJob?.cancel()
        decodeJob = launch(
            loading = true,
            onError = { toastError(R.string.toast_decoding_failed) }
        ) {
            val text = repo.decodeQrCode(uri)
            if (text.isNullOrEmpty()) toastError(R.string.toast_decoding_failed) else succeed(text)
        }
    }

    private fun succeed(text: String) {
        setState { copy(scanning = false, torchEnabled = false) }
        finishWith(BaseResult.Selected(values = listOf(text)))
    }

    override fun onCleared() {
        decodeJob?.cancel()
        super.onCleared()
    }
}
