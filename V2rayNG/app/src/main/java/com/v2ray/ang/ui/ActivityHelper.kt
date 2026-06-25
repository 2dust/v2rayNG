package com.v2ray.ang.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.FileChooserHelper
import com.v2ray.ang.helper.PermissionHelper
import com.v2ray.ang.helper.QRCodeScannerHelper
import com.v2ray.ang.util.MyContextWrapper

abstract class ActivityHelper : ComponentActivity() {
    private lateinit var fileChooser: FileChooserHelper
    private lateinit var permissionRequester: PermissionHelper
    private lateinit var qrCodeScanner: QRCodeScannerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileChooser = FileChooserHelper(this)
        permissionRequester = PermissionHelper(this)
        qrCodeScanner = QRCodeScannerHelper(this)
    }

    /**
     * Attaches the base context with a wrapped context to support locale changes.
     *
     * @param newBase The new base context to attach.
     */
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    /**
     * Checks and requests the given permission if not already granted, then invokes the callback
     * when the permission is granted.
     *
     * @param permissionType The type of permission to request.
     * @param onGranted Callback executed when the permission is granted.
     */
    protected fun checkAndRequestPermission(
        permissionType: PermissionType,
        onGranted: () -> Unit
    ) {
        permissionRequester.request(permissionType, onGranted)
    }

    /**
     * Launches a file chooser intent to pick a file with the specified MIME type.
     *
     * @param mimeType The MIME type filter for the file picker (defaults to "* / *").
     * @param onResult Callback with the selected file URI, or null if canceled or failed.
     */
    protected fun launchFileChooser(
        mimeType: String = "*/*",
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.launch(mimeType, onResult)
    }

    /**
     * Launches a document creation intent to save a file with the given name.
     *
     * @param fileName The suggested name for the new document.
     * @param onResult Callback with the created file URI, or null if canceled or failed.
     */
    protected fun launchCreateDocument(
        fileName: String,
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.createDocument(fileName, onResult)
    }

    /**
     * Launches the QR code scanner. Requests camera permission first if not granted.
     *
     * @param onResult Callback with the scanned QR code string, or null if scanning failed or canceled.
     */
    protected fun launchQRCodeScanner(onResult: (String?) -> Unit) {
        checkAndRequestPermission(PermissionType.CAMERA) {
            qrCodeScanner.launch(onResult)
        }
    }
}
