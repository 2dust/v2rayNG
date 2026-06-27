package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

class ScannerActivity : HelperBaseActivity() {
    private val scanQrCode = registerForActivityResult(ScanCustomCode(), ::handleResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                ScannerScreen(
                    onBack = { finish() },
                    onLaunchScan = { launchScan() },
                    onSelectPhoto = { showFileChooser() }
                )
            }
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_START_SCAN_IMMEDIATE)) {
            launchScan()
        }
    }

    private fun launchScan() {
        scanQrCode.launch(
            ScannerConfig.build {
                setHapticSuccessFeedback(true)
                setShowTorchToggle(true)
                setShowCloseButton(true)
                setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
            }
        )
    }

    private fun handleResult(result: QRResult) {
        if (result is QRResult.QRSuccess) {
            finished(result.content.rawValue.orEmpty())
        } else if (result !is QRResult.QRUserCanceled) {
            finish()
        }
    }

    private fun finished(text: String) {
        val intent = Intent()
        intent.putExtra("SCAN_RESULT", text)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun showFileChooser() {
        launchFileChooser("image/*") { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                    if (text.isNullOrEmpty()) toast(R.string.toast_decoding_failed)
                    else finished(text)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code", e)
                toast(R.string.toast_decoding_failed)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onLaunchScan: () -> Unit,
    onSelectPhoto: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onLaunchScan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
            ) {
                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Launch Scanner")
            }
            
            OutlinedButton(
                onClick = onSelectPhoto,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
            ) {
                Icon(painterResource(R.drawable.ic_file_24dp), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Select from Gallery")
            }
        }
    }
}
