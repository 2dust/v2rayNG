package com.v2ray.ang.ui.scanner

import androidx.compose.runtime.Composable
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.repository.ScannerRepository
import com.v2ray.ang.ui.base.BaseHelperActivity
import com.v2ray.ang.ui.base.baseViewModels

class ScannerActivity : BaseHelperActivity() {

    private val viewModel: ScannerViewModel by baseViewModels { app, _ ->
        ScannerViewModel(ScannerRepository(app))
    }

    private val platformEventHandler: (ScannerEvent) -> Boolean = { event ->
        when (event) {
            ScannerEvent.RequestCameraPermission -> {
                requestPermission(PermissionType.CAMERA) {
                    viewModel.onAction(ScannerAction.CameraPermissionGranted)
                }
                true
            }

            ScannerEvent.PickImage -> {
                pickFile(IMAGE_MIME_TYPE) { uri ->
                    viewModel.onAction(ScannerAction.ImageSelected(uri))
                }
                true
            }
        }
    }

    @Composable
    override fun ScreenContent() = ScannerScreen(viewModel, platformEventHandler)

    private companion object {
        const val IMAGE_MIME_TYPE = "image/*"
    }
}
