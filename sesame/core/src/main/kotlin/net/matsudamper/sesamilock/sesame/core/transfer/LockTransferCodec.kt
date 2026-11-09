package net.matsudamper.sesamilock.sesame.core.transfer

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

public object LockTransferCodec {
    private const val FORMAT = "sesami-lock"
    private const val VERSION = 1
    private val secretKeyPattern = Regex("^[0-9a-fA-F]{32}$")
    private val json = Json { ignoreUnknownKeys = true }

    public fun encode(lock: RegisteredLock): String {
        val common = mapOf(
            "format" to JsonPrimitive(FORMAT),
            "version" to JsonPrimitive(VERSION),
            "name" to JsonPrimitive(lock.displayName),
            "route" to JsonPrimitive(lock.route.name),
        )
        val fields = when (val credential = lock.credential) {
            is LockCredential.Ble -> mapOf(
                "deviceUuid" to JsonPrimitive(credential.deviceUuid),
                "model" to JsonPrimitive(credential.model.deviceModel),
                "keyIndex" to JsonPrimitive(credential.keyIndexHex),
                "secretKey" to JsonPrimitive(credential.secretKeyHex),
                "publicKey" to JsonPrimitive(credential.publicKeyHex),
            )

            is LockCredential.Cloud -> mapOf(
                "deviceUuid" to JsonPrimitive(credential.deviceUuid),
                "model" to JsonPrimitive(credential.model.deviceModel),
                "apiKey" to JsonPrimitive(credential.apiKey),
                "secretKey" to JsonPrimitive(credential.secretKeyHex),
            )
        }
        return JsonObject(common + fields).toString()
    }

    public fun decode(input: String): LockTransferResult {
        val obj = runCatching { json.parseToJsonElement(input.trim()) as? JsonObject }.getOrNull()
        if (obj == null || obj.string("format") != FORMAT) {
            return LockTransferResult.Failure(LockTransferError.NOT_TRANSFER_DATA)
        }
        if ((obj["version"] as? JsonPrimitive)?.content?.toIntOrNull() != VERSION) {
            return LockTransferResult.Failure(LockTransferError.UNSUPPORTED_VERSION)
        }
        val name = obj.string("name") ?: return invalid()
        val route = LockRoute.entries.firstOrNull { it.name == obj.string("route") } ?: return invalid()
        val deviceUuid = obj.string("deviceUuid") ?: return invalid()
        val model = obj.string("model")?.let(SesameModel::fromDeviceModel) ?: return invalid()
        val secretKey = obj.string("secretKey") ?: return invalid()
        val credential = when (route) {
            LockRoute.BLE -> {
                val keyIndex = obj.string("keyIndex") ?: return invalid()
                val publicKey = obj.string("publicKey") ?: return invalid()
                val result = SesameKeyImporter.validate(deviceUuid, model.deviceModel, keyIndex, secretKey, publicKey)
                (result as? KeyImportResult.Success)?.credential ?: return invalid()
            }

            LockRoute.CLOUD -> {
                val id = LockId.of(deviceUuid, LockRoute.CLOUD) ?: return invalid()
                val apiKey = obj.string("apiKey")?.takeIf { it.isNotBlank() } ?: return invalid()
                if (!secretKeyPattern.matches(secretKey)) return invalid()
                LockCredential.Cloud(
                    deviceUuid = id.deviceUuid,
                    model = model,
                    apiKey = apiKey,
                    secretKeyHex = secretKey.lowercase(Locale.ROOT),
                )
            }
        }
        return LockTransferResult.Success(displayName = name, credential = credential)
    }

    private fun invalid(): LockTransferResult = LockTransferResult.Failure(LockTransferError.INVALID_CONTENT)

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
