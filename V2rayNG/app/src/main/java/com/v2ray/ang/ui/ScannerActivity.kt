package com.v2ray.ang.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Size as TargetSize
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : ActivityHelper() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                ScannerScreen(
                    onBackClick = { finish() },
                    onSelectPhoto = { showFileChooser() },
                    onScanResult = { text -> finished(text) }
                )
            }
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
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val text = QRCodeDecoder.syncDecodeQRCode(bitmap)
                if (text.isNullOrEmpty()) {
                    toast(R.string.toast_decoding_failed)
                } else {
                    finished(text)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to decode QR code from file", e)
                toast(R.string.toast_decoding_failed)
            }
        }
    }
}

@Composable
fun ScannerScreen(
    onBackClick: () -> Unit,
    onSelectPhoto: () -> Unit,
    onScanResult: (String) -> Unit
) {
    var hasTorch by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.menu_item_import_config_qrcode),
                onBackClick = onBackClick,
                actions = {
                    if (hasTorch) {
                        IconButton(onClick = {
                            torchEnabled = !torchEnabled
                            cameraControl?.enableTorch(torchEnabled)
                        }) {
                            Icon(
                                painterResource(
                                    if (torchEnabled) R.drawable.ic_flash_on_24dp
                                    else R.drawable.ic_flash_off_24dp
                                ),
                                contentDescription = "Torch"
                            )
                        }
                    }
                    IconButton(onClick = onSelectPhoto) {
                        Icon(painterResource(R.drawable.ic_image_24dp), contentDescription = null)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CameraXPreview(
                onScanResult = onScanResult,
                onCameraReady = { control, info ->
                    cameraControl = control
                    hasTorch = info.hasFlashUnit()
                }
            )
            ScannerOverlay()
        }
    }
}

@Composable
fun ScannerOverlay() {
    val scanBoxSize = 250.dp
    val cornerLength = 24.dp
    val cornerWidth = 3.dp
    val cornerColor = Color(0xFF4CAF50)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSizePx = scanBoxSize.toPx()
            val left = (canvasWidth - boxSizePx) / 2f
            val top = (canvasHeight - boxSizePx) / 2f

            drawRect(color = Color(0x80000000))

            drawRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                blendMode = BlendMode.Clear
            )
        }

        Box(
            modifier = Modifier
                .size(scanBoxSize)
                .align(Alignment.Center)
        ) {
            // 四个角的绿色边框
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(cornerLength)
                    .height(cornerWidth)
                    .background(cornerColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(cornerWidth)
                    .height(cornerLength)
                    .background(cornerColor)
            )
        }

        Text(
            text = stringResource(R.string.menu_item_scan_qrcode),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = scanBoxSize / 2 + 24.dp)
        )
    }
}

@Composable
fun CameraXPreview(
    onScanResult: (String) -> Unit,
    onCameraReady: (CameraControl, CameraInfo) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val foundResult = remember { AtomicBoolean(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analysisExecutor = Executors.newSingleThreadExecutor()

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            TargetSize(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            processImageProxy(imageProxy, foundResult) { result ->
                                onScanResult(result)
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera.cameraControl, camera.cameraInfo)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "CameraX bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    foundResult: AtomicBoolean,
    onResult: (String) -> Unit
) {
    if (foundResult.get()) {
        imageProxy.close()
        return
    }

    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height

        val source = PlanarYUVLuminanceSource(
            bytes, width, height,
            0, 0, width, height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )

        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap, hints)
        val text = result.text

        if (!text.isNullOrEmpty() && foundResult.compareAndSet(false, true)) {
            onResult(text)
        }
    } catch (_: Exception) {
        // do nothing
    } finally {
        imageProxy.close()
    }
}
