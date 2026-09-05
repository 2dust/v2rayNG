package com.v2ray.ang.ui.scanner

import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

private val QrDecodeHints = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
    DecodeHintType.CHARACTER_SET to "UTF-8"
)

/**
 * Per-binding QR decoder for camera frames. Kept out of the Composable file so the UI layer holds
 * no decoding logic and this class stays unit-testable.
 *
 * Not thread safe by design: [MultiFormatReader] is not, and the scratch buffer is reused across
 * frames. One instance belongs to exactly one single-threaded analysis executor.
 */
internal class QrFrameDecoder {

    private val reader = MultiFormatReader().apply { setHints(QrDecodeHints) }

    /**
     * Grow-only scratch buffer for the Y plane: a full frame is ~1.4 MB at 1280x720, and
     * allocating it 30 times per second used to dominate the analysis cost.
     */
    private var bytes = ByteArray(0)

    /** @return the decoded text, or null when the frame carries no readable QR code. */
    fun decode(imageProxy: ImageProxy): String? {
        val yPlane = imageProxy.planes[0]
        // Camera HALs may pad each Y row. ZXing advances rows by dataWidth, so the reported row
        // stride must be used instead of the visible image width.
        val source = PlanarYUVLuminanceSource(
            fill(yPlane.buffer),
            yPlane.rowStride,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        return try {
            // decodeWithState reuses the hints and the reader state set up once per binding.
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            // NotFoundException on almost every frame; logging would flood the log.
            null
        }
    }

    private fun fill(source: ByteBuffer): ByteArray {
        val size = source.remaining()
        if (bytes.size < size) bytes = ByteArray(size)
        source.duplicate().get(bytes, 0, size)
        return bytes
    }
}
