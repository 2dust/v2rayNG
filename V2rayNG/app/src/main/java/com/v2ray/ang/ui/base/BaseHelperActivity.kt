package com.v2ray.ang.ui.base

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.helper.FileChooserHelper
import com.v2ray.ang.helper.PermissionHelper
import com.v2ray.ang.helper.QRCodeScannerHelper

/**
 * Platform capabilities exposed to composables through [LocalPlatformActions], so no screen needs
 * an Activity reference or a `context as? XxxActivity` cast.
 */
interface PlatformActions {
    fun requestPermission(type: PermissionType, onGranted: () -> Unit)
    fun pickFile(mimeType: String = "*/*", onResult: (Uri?) -> Unit)
    fun createDocument(fileName: String, onResult: (Uri?) -> Unit)
    fun scanQrCode(onResult: (String?) -> Unit)
}

/** No-op fallback used by hosts that do not implement [PlatformActions] (and by previews). */
internal object NoPlatformActions : PlatformActions {
    override fun requestPermission(type: PermissionType, onGranted: () -> Unit) = Unit
    override fun pickFile(mimeType: String, onResult: (Uri?) -> Unit) = onResult(null)
    override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) = onResult(null)
    override fun scanQrCode(onResult: (String?) -> Unit) = onResult(null)
}

val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> { NoPlatformActions }

/** Base Activity for screens needing file access, runtime permissions or QR scanning. */
abstract class BaseHelperActivity : BaseActivity(), PlatformActions {

    private lateinit var fileChooser: FileChooserHelper
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var qrCodeScanner: QRCodeScannerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        // Helpers register ActivityResultLaunchers, which must happen before STARTED.
        fileChooser = FileChooserHelper(this)
        permissionHelper = PermissionHelper(this)
        qrCodeScanner = QRCodeScannerHelper(this)
        super.onCreate(savedInstanceState)
    }

    override fun requestPermission(type: PermissionType, onGranted: () -> Unit) =
        permissionHelper.request(type, onGranted)

    override fun pickFile(mimeType: String, onResult: (Uri?) -> Unit) =
        fileChooser.launch(mimeType, onResult)

    override fun createDocument(fileName: String, onResult: (Uri?) -> Unit) =
        fileChooser.createDocument(fileName, onResult)

    override fun scanQrCode(onResult: (String?) -> Unit) = qrCodeScanner.launch(onResult)
}
