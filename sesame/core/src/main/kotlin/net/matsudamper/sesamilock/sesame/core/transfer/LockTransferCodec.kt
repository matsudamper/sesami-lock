package net.matsudamper.sesamilock.sesame.core.transfer

import java.util.Locale
import net.matsudamper.sesamilock.sesame.core.key.KeyImportResult
import net.matsudamper.sesamilock.sesame.core.key.SesameKeyImporter
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

public sealed interface LockTransferResult {
    public class Success(public val displayName: String, public val credential: LockCredential) : LockTransferResult {
        override fun toString(): String = "LockTransferResult.Success(credential=$credential)"
    }

    public data class Failure(val error: LockTransferError) : LockTransferResult
}

public enum class LockTransferError {
    NOT_TRANSFER_DATA,
    UNSUPPORTED_VERSION,
    INVALID_CONTENT,
}

/**
 * このアプリ専用の鍵 1 件分の転送形式。`|` 区切りで先頭がバージョン (`SL1`)。
 * BLE: `SL1|BLE|uuid|productType|keyIndex|secretKey|publicKey|表示名`
 * クラウド: `SL1|CLOUD|uuid|productType|secretKey|apiKey|表示名`
 * 表示名は最後のフィールドで区切りを含んでもそのまま続け、容量を増やさないためエスケープしない。
 * それより前の自由入力（API キー）だけ `\|` と `\\` でエスケープする。
 */
public object LockTransferCodec {
    private const val SEPARATOR = '|'
    private const val ESCAPE = '\\'
    private const val VERSION_TAG = "SL1"
    private val versionTagPattern = Regex("^SL[0-9]+$")
    private val secretKeyPattern = Regex("^[0-9a-fA-F]{32}$")

    public fun encode(lock: RegisteredLock): String {
        val fields = when (val credential = lock.credential) {
            is LockCredential.Ble -> listOf(
                VERSION_TAG,
                LockRoute.BLE.name,
                credential.deviceUuid,
                credential.model.productType.toString(),
                credential.keyIndexHex,
                credential.secretKeyHex,
                credential.publicKeyHex,
            )

            is LockCredential.Cloud -> listOf(
                VERSION_TAG,
                LockRoute.CLOUD.name,
                credential.deviceUuid,
                credential.model.productType.toString(),
                credential.secretKeyHex,
                escape(credential.apiKey),
            )
        }
        return (fields + lock.displayName).joinToString(SEPARATOR.toString())
    }

    public fun decode(input: String): LockTransferResult {
        val reader = FieldReader(input.trim())
        val versionTag = reader.next()
        if (versionTag == null || !versionTagPattern.matches(versionTag)) {
            return LockTransferResult.Failure(LockTransferError.NOT_TRANSFER_DATA)
        }
        if (versionTag != VERSION_TAG) return LockTransferResult.Failure(LockTransferError.UNSUPPORTED_VERSION)
        val routeName = reader.next()
        val route = LockRoute.entries.firstOrNull { it.name == routeName } ?: return invalid()
        val deviceUuid = reader.next() ?: return invalid()
        val model = reader.next()?.toIntOrNull()?.let(SesameModel::fromProductType) ?: return invalid()
        val credential = when (route) {
            LockRoute.BLE -> {
                val keyIndex = reader.next() ?: return invalid()
                val secretKey = reader.next() ?: return invalid()
                val publicKey = reader.next() ?: return invalid()
                val result = SesameKeyImporter.validate(deviceUuid, model.deviceModel, keyIndex, secretKey, publicKey)
                (result as? KeyImportResult.Success)?.credential ?: return invalid()
            }

            LockRoute.CLOUD -> {
                val secretKey = reader.next() ?: return invalid()
                val apiKey = reader.next()?.takeIf { it.isNotBlank() } ?: return invalid()
                val id = LockId.of(deviceUuid, LockRoute.CLOUD) ?: return invalid()
                if (!secretKeyPattern.matches(secretKey)) return invalid()
                LockCredential.Cloud(
                    deviceUuid = id.deviceUuid,
                    model = model,
                    apiKey = apiKey,
                    secretKeyHex = secretKey.lowercase(Locale.ROOT),
                )
            }
        }
        return LockTransferResult.Success(displayName = reader.rest(), credential = credential)
    }

    private fun invalid(): LockTransferResult = LockTransferResult.Failure(LockTransferError.INVALID_CONTENT)

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            if (char == SEPARATOR || char == ESCAPE) append(ESCAPE)
            append(char)
        }
    }

    private class FieldReader(private val text: String) {
        private var position = 0

        fun next(): String? {
            val field = StringBuilder()
            while (position < text.length) {
                val char = text[position++]
                when {
                    char == ESCAPE && position < text.length -> field.append(text[position++])
                    char == SEPARATOR -> return field.toString()
                    else -> field.append(char)
                }
            }
            return null
        }

        fun rest(): String = text.substring(position).also { position = text.length }
    }
}
