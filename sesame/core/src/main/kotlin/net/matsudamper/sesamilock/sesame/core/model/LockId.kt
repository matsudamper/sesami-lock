package net.matsudamper.sesamilock.sesame.core.model

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@JvmInline
public value class LockId private constructor(public val value: String) {
    public fun shortHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(SHORT_HASH_BYTES).joinToString("") { "%02x".format(it) }
    }

    override fun toString(): String = "LockId(${shortHash()})"

    public companion object {
        private const val SHORT_HASH_BYTES = 4

        public fun fromDeviceUuid(deviceUuid: String): LockId? {
            val uuid = runCatching { UUID.fromString(deviceUuid) }.getOrNull() ?: return null
            return LockId(uuid.toString().uppercase(Locale.ROOT))
        }
    }
}
