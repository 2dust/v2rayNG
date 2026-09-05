package com.v2ray.ang.ui.scanner

import android.content.res.Configuration
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.LocalAppColors
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Size as TargetSize

private val ScanBoxSize = 250.dp
private val CornerLength = 24.dp
private val CornerThickness = 3.dp
private val OverlayHintOffset = 24.dp
private const val CameraScrimAlpha = 0.5f
private const val AnalysisWidth = 1280
private const val AnalysisHeight = 720

/**
 * Overlay for the camera preview: dimming with a clear scan box, corner marks and a hint.
 *
 * The scrim is punched through with [BlendMode.Clear], which only reveals the camera because the
 * viewfinder below uses [ImplementationMode.EXTERNAL] (a SurfaceView behind the window). Switching
 * the viewfinder to EMBEDDED would turn the hole black - keep the two in sync.
 */
@Composable
internal fun ScannerOverlay(modifier: Modifier = Modifier) {
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = CameraScrimAlpha)
    val cornerColor = MaterialTheme.colorScheme.tertiary
    val hintColor = LocalAppColors.current.onCameraPreview

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSizePx = ScanBoxSize.toPx()
            val left = (size.width - boxSizePx) / 2f
            val top = (size.height - boxSizePx) / 2f
            drawRect(color = scrimColor)
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSizePx, boxSizePx),
                blendMode = BlendMode.Clear
            )
        }
        Box(
            modifier = Modifier
                .size(ScanBoxSize)
                .align(Alignment.Center)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).forEach { align ->
                Box(
                    modifier = Modifier
                        .align(align)
                        .width(CornerLength)
                        .height(CornerThickness)
                        .background(cornerColor)
                )
                Box(
                    modifier = Modifier
                        .align(align)
                        .width(CornerThickness)
                        .height(CornerLength)
                        .background(cornerColor)
                )
            }
        }
        Text(
            text = stringResource(R.string.menu_item_scan_qrcode),
            color = hintColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = ScanBoxSize / 2 + OverlayHintOffset)
        )
    }
}

/**
 * CameraX preview plus QR analysis. Every platform handle (CameraControl, CameraInfo, ImageProxy)
 * stays inside this file: the screen only states the *intent* ([torchEnabled]) and receives facts.
 *
 * @param torchEnabled projected onto the bound camera; safe to set before the camera exists.
 * @param onDecoded invoked once per binding, on the analysis thread.
 * @param onCameraReady reports whether the bound camera actually has a flash unit.
 * @param onCameraFailed the camera could not be bound; the screen must leave the scanning state.
 */
@Composable
internal fun ScannerCameraPreview(
    torchEnabled: Boolean,
    onDecoded: (String) -> Unit,
    onCameraReady: (Boolean) -> Unit,
    onCameraFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The binding outlives a recomposition, so the callbacks must be read through the latest
    // value instead of the one captured when DisposableEffect first ran.
    val decodedHandler by rememberUpdatedState(onDecoded)
    val readyHandler by rememberUpdatedState(onCameraReady)
    val failedHandler by rememberUpdatedState(onCameraFailed)

    // Intentionally not snapshot state: publishing the control must not invalidate this
    // composable, it is only ever consumed by the torch effect below.
    val cameraControl = remember { MutableStateFlow<CameraControl?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    // Restarts on every torch flip and re-applies as soon as a control is published, which also
    // covers rebinding the camera while the torch is already on.
    LaunchedEffect(cameraControl, torchEnabled) {
        cameraControl.collect { control -> control?.enableTorch(torchEnabled) }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        // One decoder per binding: MultiFormatReader is not thread safe and the scratch buffer is
        // confined to the single analysis thread.
        val decoder = QrFrameDecoder()
        val foundResult = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            TargetSize(AnalysisWidth, AnalysisHeight),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(analysisExecutor) { imageProxy ->
                    val text = try {
                        if (foundResult.get()) null else decoder.decode(imageProxy)
                    } finally {
                        imageProxy.close()
                    }
                    if (!text.isNullOrEmpty() && foundResult.compareAndSet(false, true)) {
                        // Safe off the main thread: it ends in MutableStateFlow.update /
                        // Channel.trySend, both of which are thread safe.
                        decodedHandler(text)
                    }
                }
            }

        val preview = CameraPreview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                // The future may complete after the preview left the composition; binding then
                // would leak the camera and call a dead callback.
                if (disposed) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    // Clears use cases a previous, abruptly destroyed binding may have left over.
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraControl.value = camera.cameraControl
                    readyHandler(camera.cameraInfo.hasFlashUnit())
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "CameraX bind failed", e)
                    failedHandler()
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            disposed = true
            imageAnalysis.clearAnalyzer()
            runCatching { cameraProvider?.unbind(preview, imageAnalysis) }
            analysisExecutor.shutdownNow()
            cameraControl.value = null
            surfaceRequest = null
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            implementationMode = ImplementationMode.EXTERNAL,
            modifier = modifier.fillMaxSize()
        )
    }
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScannerOverlayPreview() = AppTheme {
    ScannerOverlay()
}
