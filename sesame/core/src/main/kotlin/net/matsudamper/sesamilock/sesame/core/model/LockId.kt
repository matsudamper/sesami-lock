package net.matsudamper.sesamilock.sesame.core.model

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@JvmInline
public value class LockId private constructor(public val value: String) {
    public val deviceUuid: String get() = value.substringAfter(SEPARATOR)

    public fun shortHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(SHORT_HASH_BYTES).joinToString("") { "%02x".format(it) }
    }

    override fun toString(): String = "LockId(${shortHash()})"

    public companion object {
        private const val SHORT_HASH_BYTES = 4
        private const val SEPARATOR = ':'

        public fun of(deviceUuid: String, route: LockRoute): LockId? {
            val uuid = runCatching { UUID.fromString(deviceUuid) }.getOrNull() ?: return null
            return LockId("${route.name}$SEPARATOR${uuid.toString().uppercase(Locale.ROOT)}")
        }

        public fun parse(value: String): LockId? {
            val routeName = value.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val route = LockRoute.entries.firstOrNull { it.name == routeName } ?: return null
            return of(value.substringAfter(SEPARATOR), route)?.takeIf { it.value == value }
        }
    }
}
