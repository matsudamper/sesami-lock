package net.matsudamper.sesamilock

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

internal class QrImageDecoder(private val contentResolver: ContentResolver) {
    suspend fun decode(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(uri) ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            decode(source) ?: decode(source.invert())
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(source: LuminanceSource): String? = listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source))
        .firstNotNullOfOrNull { binarizer ->
            try {
                QRCodeReader().decode(BinaryBitmap(binarizer), decodeHints).text
            } catch (_: ReaderException) {
                null
            }
        }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = generateSequence(1) { it * 2 }.first { longestSide / it <= MAX_DECODE_SIDE }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        private const val MAX_DECODE_SIDE = 2048
        private val decodeHints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
    }
}
