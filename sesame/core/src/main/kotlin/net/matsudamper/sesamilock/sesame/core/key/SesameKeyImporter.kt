package net.matsudamper.sesamilock.sesame.core.key

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

public sealed interface KeyImportResult {
    public data class Success(val credential: LockCredential.Ble, val suggestedName: String?) : KeyImportResult

    public data class Failure(val error: KeyImportError) : KeyImportResult
}

public enum class KeyImportError {
    EMPTY,
    UNRECOGNIZED_FORMAT,
    ENCRYPTED_TOKEN_QR,
    INVALID_ENCODING,
    UNSUPPORTED_MODEL,
    INVALID_UUID,
    INVALID_KEY_FORMAT,
    SERVER_SIGNED_GUEST_KEY,
}

public object SesameKeyImporter {
    private const val QR_SCHEME = "ssm"
    private const val SECRET_KEY_BYTES = 16
    private const val KEY_INDEX_BYTES = 2
    private const val UUID_BYTES = 16
    private const val GUEST_KEY_MARKER = "000000"
    private val hexPattern = Regex("^[0-9a-fA-F]*$")
    private val json = Json { ignoreUnknownKeys = true }

    public fun import(input: String): KeyImportResult {
        val trimmed = input.trim()
        return when {
            trimmed.isEmpty() -> KeyImportResult.Failure(KeyImportError.EMPTY)
            trimmed.startsWith("{") -> importJson(trimmed)
            trimmed.startsWith("$QR_SCHEME:", ignoreCase = true) -> importLegacyQr(trimmed)
            else -> KeyImportResult.Failure(KeyImportError.UNRECOGNIZED_FORMAT)
        }
    }

    public fun validate(
        deviceUuid: String,
        deviceModel: String,
        keyIndexHex: String,
        secretKeyHex: String,
        publicKeyHex: String,
    ): KeyImportResult {
        val uuid = runCatching { UUID.fromString(deviceUuid) }.getOrNull()
            ?: return KeyImportResult.Failure(KeyImportError.INVALID_UUID)
        val model = SesameModel.fromDeviceModel(deviceModel)
            ?: return KeyImportResult.Failure(KeyImportError.UNSUPPORTED_MODEL)
        val formatValid = isHexOfBytes(secretKeyHex, SECRET_KEY_BYTES) &&
            isHexOfBytes(keyIndexHex, KEY_INDEX_BYTES) &&
            isHexOfBytes(publicKeyHex, model.protocol.publicKeyBytes)
        if (!formatValid) return KeyImportResult.Failure(KeyImportError.INVALID_KEY_FORMAT)
        if (secretKeyHex.contains(GUEST_KEY_MARKER)) return KeyImportResult.Failure(KeyImportError.SERVER_SIGNED_GUEST_KEY)
        return KeyImportResult.Success(
            credential = LockCredential.Ble(
                deviceUuid = uuid.toString().uppercase(Locale.ROOT),
                model = model,
                keyIndexHex = keyIndexHex.lowercase(Locale.ROOT),
                secretKeyHex = secretKeyHex.lowercase(Locale.ROOT),
                publicKeyHex = publicKeyHex.lowercase(Locale.ROOT),
            ),
            suggestedName = null,
        )
    }

    private fun importJson(input: String): KeyImportResult {
        val obj = runCatching { json.parseToJsonElement(input) as? JsonObject }.getOrNull()
            ?: return KeyImportResult.Failure(KeyImportError.INVALID_ENCODING)
        val fields = listOf("deviceUUID", "deviceModel", "keyIndex", "secretKey", "sesame2PublicKey").map { name ->
            (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return KeyImportResult.Failure(KeyImportError.INVALID_KEY_FORMAT)
        }
        return validate(
            deviceUuid = fields[0],
            deviceModel = fields[1],
            keyIndexHex = fields[2],
            secretKeyHex = fields[3],
            publicKeyHex = fields[4],
        )
    }

    /**
     * 公式アプリの「従来形式」の共有 QR (`ssm://UI?t=sk&sk=...&n=...`) を読む。
     * `sk` は `productType(1) + secretKey(16) + publicKey(OS2:64 / OS3:4) + keyIndex(2) + deviceUUID(16)`。
     * 長さが機種の想定と一致しないもの（暗号化トークン形式など）は推測せずに拒否する。
     */
    private fun importLegacyQr(input: String): KeyImportResult {
        val query = runCatching { URI(input).rawQuery }.getOrNull()
            ?: return KeyImportResult.Failure(KeyImportError.UNRECOGNIZED_FORMAT)
        val params = query.split("&").mapNotNull { pair ->
            val separator = pair.indexOf('=').takeIf { it >= 0 } ?: return@mapNotNull null
            val name = pair.substring(0, separator)
            val value = URLDecoder.decode(pair.substring(separator + 1).replace("+", "%2B"), Charsets.UTF_8)
            name to value
        }.toMap()
        if (params["t"] != "sk") return KeyImportResult.Failure(KeyImportError.UNRECOGNIZED_FORMAT)
        val encoded = params["sk"] ?: return KeyImportResult.Failure(KeyImportError.UNRECOGNIZED_FORMAT)
        val bytes = decodeBase64(encoded) ?: return KeyImportResult.Failure(KeyImportError.INVALID_ENCODING)
        if (bytes.isEmpty()) return KeyImportResult.Failure(KeyImportError.INVALID_ENCODING)

        val model = SesameModel.fromProductType(bytes[0].toInt() and 0xFF)
        val publicKeyBytes = model?.protocol?.publicKeyBytes
        val expectedSize = publicKeyBytes?.let { 1 + SECRET_KEY_BYTES + it + KEY_INDEX_BYTES + UUID_BYTES }
        return when {
            model == null || publicKeyBytes == null -> KeyImportResult.Failure(KeyImportError.UNSUPPORTED_MODEL)

            bytes.size != expectedSize -> KeyImportResult.Failure(KeyImportError.ENCRYPTED_TOKEN_QR)

            else -> {
                val buffer = ByteBuffer.wrap(bytes, 1, bytes.size - 1)
                val secretKey = buffer.take(SECRET_KEY_BYTES)
                val publicKey = buffer.take(publicKeyBytes)
                val keyIndex = buffer.take(KEY_INDEX_BYTES)
                val uuidBuffer = ByteBuffer.wrap(buffer.take(UUID_BYTES))
                val uuid = UUID(uuidBuffer.long, uuidBuffer.long)
                when (val result = validate(uuid.toString(), model.deviceModel, keyIndex.toHex(), secretKey.toHex(), publicKey.toHex())) {
                    is KeyImportResult.Failure -> result
                    is KeyImportResult.Success -> result.copy(suggestedName = params["n"]?.takeIf { it.isNotBlank() })
                }
            }
        }
    }

    private fun decodeBase64(value: String): ByteArray? = runCatching { Base64.getDecoder().decode(value) }.getOrNull()
        ?: runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()

    private fun isHexOfBytes(value: String, bytes: Int): Boolean = value.length == bytes * 2 && hexPattern.matches(value)

    private fun ByteBuffer.take(size: Int): ByteArray = ByteArray(size).also { get(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
