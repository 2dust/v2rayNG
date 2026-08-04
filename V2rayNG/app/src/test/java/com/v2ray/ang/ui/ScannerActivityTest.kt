package com.v2ray.ang.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class ScannerActivityTest {

    @Test
    fun paddedCameraRowsDecodeQrCode() {
        val payload = "vless://00000000-0000-4000-8000-000000006007@example.com:443" +
            "?encryption=none&security=tls&type=tcp#issue-6007-gallery"
        val width = 320
        val height = 320
        val rowStride = width + 32
        val qrCode = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, width, height)
        val paddedYPlane = ByteArray(rowStride * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                paddedYPlane[y * rowStride + x] = if (qrCode[x, y]) 0 else 0xFF.toByte()
            }
            for (x in width until rowStride) {
                paddedYPlane[y * rowStride + x] = (x + y).toByte()
            }
        }

        val source = createYPlaneLuminanceSource(
            buffer = ByteBuffer.wrap(paddedYPlane),
            width = width,
            height = height,
            rowStride = rowStride,
        )
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)

        assertEquals(payload, result.text)
    }
}
