package com.v2ray.ang.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.*

/**
 * 描述:解析二维码图片
 */
object QRCodeDecoder {
    val HINTS: MutableMap<DecodeHintType, Any?> = EnumMap(DecodeHintType::class.java)

    /**
     * 同步解析本地图片二维码。该方法是耗时操作，请在子线程中调用。
     *
     * @param picturePath 要解析的二维码图片本地路径
     * @return 返回二维码图片里的内容 或 null
     */
    fun syncDecodeQRCode(picturePath: String): String? {
        return syncDecodeQRCode(getDecodeAbleBitmap(picturePath))
    }

    /**
     * 同步解析bitmap二维码。该方法是耗时操作，请在子线程中调用。
     *
     * @param bitmap 要解析的二维码图片
     * @return 返回二维码图片里的内容 或 null
     */
    fun syncDecodeQRCode(bitmap: Bitmap?): String? {
        var source: RGBLuminanceSource? = null
        try {
            val width = bitmap!!.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            source = RGBLuminanceSource(width, height, pixels)
            return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (source != null) {
            try {
                return MultiFormatReader().decode(BinaryBitmap(GlobalHistogramBinarizer(source)), HINTS).text
            } catch (e2: Throwable) {
                e2.printStackTrace()
            }
        }
        return null
    }

    /**
     * 将本地图片文件转换成可解码二维码的 Bitmap。为了避免图片太大，这里对图片进行了压缩。感谢 https://github.com/devilsen 提的 PR
     *
     * @param picturePath 本地图片文件路径
     * @return
     */
    private fun getDecodeAbleBitmap(picturePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(picturePath, options)
            var sampleSize = options.outHeight / 400
            if (sampleSize <= 0) {
                sampleSize = 1
            }
            options.inSampleSize = sampleSize
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(picturePath, options)
        } catch (e: Exception) {
            null
        }
    }

    init {
        val allFormats: List<BarcodeFormat> = arrayListOf(
                BarcodeFormat.AZTEC
                ,BarcodeFormat.CODABAR
                ,BarcodeFormat.CODE_39
                ,BarcodeFormat.CODE_93
                ,BarcodeFormat.CODE_128
                ,BarcodeFormat.DATA_MATRIX
                ,BarcodeFormat.EAN_8
                ,BarcodeFormat.EAN_13
                ,BarcodeFormat.ITF
                ,BarcodeFormat.MAXICODE
                ,BarcodeFormat.PDF_417
                ,BarcodeFormat.QR_CODE
                ,BarcodeFormat.RSS_14
                ,BarcodeFormat.RSS_EXPANDED
                ,BarcodeFormat.UPC_A
                ,BarcodeFormat.UPC_E
                ,BarcodeFormat.UPC_EAN_EXTENSION)
        HINTS[DecodeHintType.TRY_HARDER] = BarcodeFormat.QR_CODE
        HINTS[DecodeHintType.POSSIBLE_FORMATS] = allFormats
        HINTS[DecodeHintType.CHARACTER_SET] = "utf-8"
    }
}
