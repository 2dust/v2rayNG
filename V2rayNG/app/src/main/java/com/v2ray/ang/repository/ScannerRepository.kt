package com.v2ray.ang.repository

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import java.io.InputStream

/**
 * Data layer of the scanner feature. Bitmap decoding is expensive, so it runs on IO.
 */
open class ScannerRepository(private val app: Application) : BaseRepository() {

    /** @return the QR code content, or null when the image cannot be read or decoded. */
    open suspend fun decodeQrCode(uri: Uri): String? = withIO {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        readImage(uri) { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withIO null

        // A modern gallery photo is 4000px+ wide. Decoding it at full size costs ~64 MB here and
        // another ~64 MB for the IntArray inside QRCodeDecoder, so the long edge is capped first.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = readImage(uri) { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return@withIO null

        try {
            QRCodeDecoder.syncDecodeQRCode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun readImage(uri: Uri, decode: (InputStream) -> Bitmap?): Bitmap? =
        runCatching {
            app.contentResolver.openInputStream(uri)?.use(decode)
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to read image from URI", it)
        }.getOrNull()

    /** Smallest power-of-two sample size that keeps the long edge at or below the cap. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        val longEdge = maxOf(width, height)
        var sample = 1
        while (longEdge / sample > MAX_DECODE_EDGE_PX) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_DECODE_EDGE_PX = 1600
    }
}
