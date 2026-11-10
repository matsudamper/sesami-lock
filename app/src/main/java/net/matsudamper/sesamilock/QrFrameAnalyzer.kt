package net.matsudamper.sesamilock

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * カメラのフレームから QR コードを探す。端末を重くしないよう、前回の解析から一定時間たつまでのフレームは解析せずに捨て、
 * 一度読めたら以降のフレームは解析しない。
 */
internal class QrFrameAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val decoded = AtomicBoolean(false)
    private var lastAnalyzedNanos = Long.MIN_VALUE

    override fun analyze(image: ImageProxy) {
        image.use {
            val timestamp = it.imageInfo.timestamp
            val intervalElapsed = lastAnalyzedNanos == Long.MIN_VALUE || timestamp - lastAnalyzedNanos >= ANALYSIS_INTERVAL_NANOS
            if (decoded.get() || !intervalElapsed) return
            lastAnalyzedNanos = timestamp
            val source = it.toLuminanceSource()
            val text = decode(source) ?: decode(source.invert())
            if (text != null && decoded.compareAndSet(false, true)) onDecoded(text)
        }
    }

    private fun ImageProxy.toLuminanceSource(): LuminanceSource {
        val luminancePlane = planes[0]
        val buffer = luminancePlane.buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return PlanarYUVLuminanceSource(bytes, luminancePlane.rowStride, height, 0, 0, width, height, false)
    }

    private fun decode(source: LuminanceSource): String? = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private companion object {
        private val ANALYSIS_INTERVAL_NANOS = 300.milliseconds.inWholeNanoseconds
        private val decodeHints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
    }
}
