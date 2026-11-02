package net.matsudamper.sesamilock.sesame.credential

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

@Serializable
internal data class StoredLocks(val version: Int, val locks: List<StoredLock>)

@Serializable
internal data class StoredLock(
    val deviceUuid: String,
    val displayName: String,
    val deviceModel: String,
    val ble: StoredBleKey?,
    val cloud: StoredCloudCredential?,
)

@Serializable
internal data class StoredBleKey(val keyIndexHex: String, val secretKeyHex: String, val publicKeyHex: String)

@Serializable
internal data class StoredCloudCredential(val apiKey: String, val secretKeyHex: String)

internal object StoredLocksCodec {
    private const val CURRENT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(locks: List<RegisteredLock>): ByteArray {
        val stored = StoredLocks(CURRENT_VERSION, locks.map { it.toStored() })
        return json.encodeToString(StoredLocks.serializer(), stored).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<RegisteredLock>? {
        val stored = runCatching { json.decodeFromString(StoredLocks.serializer(), bytes.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        if (stored.version != CURRENT_VERSION) return null
        return stored.locks.map { it.toRegisteredLock() ?: return null }
    }

    private fun RegisteredLock.toStored(): StoredLock = when (val credential = credential) {
        is LockCredential.Ble -> StoredLock(
            deviceUuid = credential.deviceUuid,
            displayName = displayName,
            deviceModel = credential.model.deviceModel,
            ble = StoredBleKey(credential.keyIndexHex, credential.secretKeyHex, credential.publicKeyHex),
            cloud = null,
        )

        is LockCredential.Cloud -> StoredLock(
            deviceUuid = credential.deviceUuid,
            displayName = displayName,
            deviceModel = credential.model.deviceModel,
            ble = null,
            cloud = StoredCloudCredential(credential.apiKey, credential.secretKeyHex),
        )
    }

    private fun StoredLock.toRegisteredLock(): RegisteredLock? {
        val id = LockId.fromDeviceUuid(deviceUuid) ?: return null
        val model = SesameModel.fromDeviceModel(deviceModel) ?: return null
        val credential = when {
            ble != null && cloud == null -> LockCredential.Ble(deviceUuid, model, ble.keyIndexHex, ble.secretKeyHex, ble.publicKeyHex)
            cloud != null && ble == null -> LockCredential.Cloud(deviceUuid, model, cloud.apiKey, cloud.secretKeyHex)
            else -> return null
        }
        return RegisteredLock(id, displayName, credential)
    }
}
