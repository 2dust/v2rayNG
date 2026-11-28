package com.v2ray.ang.vm

import android.app.Application
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.mobile.ScannerUiState
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : BaseViewModel(application) {

    sealed interface ScannerViewModelEvent : ViewModelEvent {
        data class ScanResult(val text: String) : ScannerViewModelEvent
    }

    private val _scanState = MutableStateFlow(ScannerUiState.IDLE)
    val scanState: StateFlow<ScannerUiState> = _scanState.asStateFlow()

    fun startScan() {
        _scanState.value = ScannerUiState.ACTIVE
    }

    fun stopScan() {
        _scanState.value = ScannerUiState.IDLE
    }

    fun decodeQrCodeFromUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    toast(R.string.toast_decoding_failed)
                } else {
                    _viewModelEvent.send(ScannerViewModelEvent.ScanResult(text))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                toast(R.string.toast_decoding_failed)
            }
        }
    }
}
