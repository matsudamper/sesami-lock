package net.matsudamper.sesamilock

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NfcOpenAppTagWriter(private val context: Context) {
    fun availability(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return when {
            adapter == null -> NfcAvailability.NotSupported
            !adapter.isEnabled -> NfcAvailability.Disabled
            else -> NfcAvailability.Ready
        }
    }

    /**
     * アプリを起動する Android Application Record だけを含む NDEF メッセージをタグへ書き込む。
     * 未フォーマットのタグは NDEF としてフォーマットしながら書き込む。
     */
    suspend fun write(tag: Tag): NfcWriteResult = withContext(Dispatchers.IO) {
        val message = NdefMessage(NdefRecord.createApplicationRecord(context.packageName))
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                writeToNdef(ndef, message)
            } else {
                formatWith(tag, message)
            }
        } catch (_: IOException) {
            NfcWriteResult.Failed
        } catch (_: FormatException) {
            NfcWriteResult.Failed
        }
    }

    private fun writeToNdef(ndef: Ndef, message: NdefMessage): NfcWriteResult = ndef.use {
        it.connect()
        when {
            !it.isWritable -> NfcWriteResult.ReadOnly

            it.maxSize < message.byteArrayLength -> NfcWriteResult.CapacityTooSmall

            else -> {
                it.writeNdefMessage(message)
                NfcWriteResult.Success
            }
        }
    }

    private fun formatWith(tag: Tag, message: NdefMessage): NfcWriteResult {
        val formatable = NdefFormatable.get(tag) ?: return NfcWriteResult.Unsupported
        return formatable.use {
            it.connect()
            it.format(message)
            NfcWriteResult.Success
        }
    }
}

internal enum class NfcAvailability {
    NotSupported,
    Disabled,
    Ready,
}

internal enum class NfcWriteResult {
    Success,
    ReadOnly,
    CapacityTooSmall,
    Unsupported,
    Failed,
}
