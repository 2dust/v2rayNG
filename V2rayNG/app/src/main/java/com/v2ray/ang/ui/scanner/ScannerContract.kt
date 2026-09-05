package com.v2ray.ang.ui.scanner

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.base.BaseAction
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseUiState

@Immutable
data class ScannerUiState(
    /** True while the camera preview and the analyzer are bound. */
    val scanning: Boolean = false,
    /** Whether the bound camera actually has a flash unit. */
    val hasTorch: Boolean = false,
    val torchEnabled: Boolean = false
) : BaseUiState

/** The complete set of user intents of the scanner screen. */
sealed interface ScannerAction : BaseAction {
    data object Back : ScannerAction
    data object StartScanClicked : ScannerAction
    data object StopScanClicked : ScannerAction

    /** The host granted CAMERA; only now may the preview be bound. */
    data object CameraPermissionGranted : ScannerAction

    /** Reported by the preview once the camera is bound. */
    data class CameraReady(val hasTorch: Boolean) : ScannerAction

    /** Binding the camera failed; the screen must leave the scanning state and say so. */
    data object CameraFailed : ScannerAction

    data object ToggleTorch : ScannerAction
    data object PickImageClicked : ScannerAction

    /** Result of the image picker; `null` means the user cancelled. */
    data class ImageSelected(val uri: Uri?) : ScannerAction

    /** A QR code recognised by the analyzer. */
    data class Decoded(val text: String) : ScannerAction
}

/**
 * Platform-level events. Only the host Activity can execute these: the ViewModel neither
 * holds a Context nor knows about the permission launcher or the SAF picker.
 */
sealed interface ScannerEvent : BaseEvent.Platform {
    data object RequestCameraPermission : ScannerEvent
    data object PickImage : ScannerEvent
}
