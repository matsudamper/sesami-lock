package net.matsudamper.sesamilock.sesame.cloud

import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.matsudamper.sesamilock.sesame.core.model.ConnectionState
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.MechanicalObservation
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.LockSnapshotStore
import net.matsudamper.sesamilock.sesame.core.transport.LockTransport
import net.matsudamper.sesamilock.sesame.core.transport.SendGate
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

public class SesameCloudTransport internal constructor(
    private val http: HttpExecutor,
    private val clock: LockClock,
    private val historyTag: String,
) : LockTransport {
    public constructor(clock: LockClock, historyTag: String) : this(
        http = UrlConnectionHttpExecutor(connectTimeout = 10.seconds, readTimeout = 15.seconds),
        clock = clock,
        historyTag = historyTag,
    )

    override val route: LockRoute = LockRoute.CLOUD
    private val credentials = ConcurrentHashMap<LockId, LockCredential.Cloud>()
    private val stores = ConcurrentHashMap<LockId, LockSnapshotStore>()
    private val shadowVersions = ConcurrentHashMap<LockId, ShadowVersion>()

    public fun register(lockId: LockId, credential: LockCredential.Cloud) {
        credentials[lockId] = credential
        store(lockId).updateConnection(ConnectionState.AUTHENTICATED, shadowVersion(lockId).current())
    }

    public fun unregister(lockId: LockId) {
        credentials.remove(lockId)
        store(lockId).updateConnection(ConnectionState.DISCONNECTED, shadowVersion(lockId).current())
    }

    override fun snapshot(lockId: LockId): StateFlow<LockSnapshot> = store(lockId).snapshot

    override suspend fun ensureConnected(lockId: LockId): ConnectionResult {
        if (!credentials.containsKey(lockId)) return ConnectionResult.Failed(TransportError.DEVICE_NOT_REGISTERED)
        val baselineKnown = fetchShadow(lockId)
        val generation = if (baselineKnown) shadowVersion(lockId).current() else UNVERIFIABLE_GENERATION
        return ConnectionResult.Ready(lockId, generation)
    }

    override fun isAuthenticated(lockId: LockId): Boolean = credentials.containsKey(lockId)

    override suspend fun dispatchExplicit(lockId: LockId, intent: LockIntent, requestId: RequestId, sendGate: SendGate): DispatchResult {
        val credential = credentials[lockId] ?: return DispatchResult.NotSent(TransportError.DEVICE_NOT_REGISTERED)
        val url = deviceUrl(credential, "/cmd") ?: return DispatchResult.NotSent(TransportError.DEVICE_NOT_REGISTERED)
        val command = SesameCloudCommand.from(intent)
        val secretKey = hexToBytes(credential.secretKeyHex) ?: return DispatchResult.NotSent(TransportError.AUTHENTICATION_FAILED)
        val body = buildJsonObject {
            put("cmd", command.code)
            put("history", Base64.getEncoder().encodeToString(historyTag.toByteArray(Charsets.UTF_8)))
            put("sign", SesameCloudSigner.sign(secretKey, clock.currentTimeMillis() / 1000))
        }.toString()
        val request = HttpRequest(
            method = "POST",
            url = url,
            headers = mapOf("x-api-key" to credential.apiKey, "Content-Type" to "application/json"),
            body = body,
        )
        return when (val result = http.execute(request, sendGate::tryCommit)) {
            HttpResult.NotConnected -> DispatchResult.NotSent(TransportError.NETWORK)

            HttpResult.NotSentByGate -> DispatchResult.NotSent(TransportError.NOT_CONNECTED)

            HttpResult.TimedOut -> DispatchResult.OutcomeUnknown(TransportError.TIMEOUT)

            HttpResult.Interrupted -> DispatchResult.OutcomeUnknown(TransportError.NETWORK)

            is HttpResult.Response -> when (result.statusCode) {
                in 200..299 -> DispatchResult.Accepted
                in 400..499 -> DispatchResult.RejectedByDevice(TransportError.HTTP_CLIENT_ERROR)
                else -> DispatchResult.OutcomeUnknown(TransportError.HTTP_SERVER_ERROR)
            }
        }
    }

    override suspend fun refreshState(lockId: LockId) {
        fetchShadow(lockId)
    }

    /**
     * Shadow を取得して観測として反映する。Shadow は Hub の切断などで古い値を返し続けることがあるため、
     * 応答の `timestamp` が前回と変わったときだけ世代を進め、変わらない Shadow を新しい観測として扱わない。
     * 単位が資料で確定しないので、`timestamp` は値の比較にだけ使い時刻として解釈しない。
     * @return 世代の基準になる `timestamp` を取得できたら true
     */
    private suspend fun fetchShadow(lockId: LockId): Boolean {
        val credential = credentials[lockId] ?: return false
        val url = deviceUrl(credential, "") ?: return false
        val request = HttpRequest(
            method = "GET",
            url = url,
            headers = mapOf("x-api-key" to credential.apiKey),
            body = null,
        )
        val response = http.execute(request, beforeSend = { true }) as? HttpResult.Response ?: return false
        if (response.statusCode !in 200..299) return false
        val shadow = parseShadow(response.body) ?: return false
        val generation = shadowVersion(lockId).advanceIfChanged(shadow.rawTimestamp)
        val store = store(lockId)
        store.updateConnection(ConnectionState.AUTHENTICATED, generation)
        store.offerObservation(
            MechanicalObservation(
                state = shadow.state,
                observedAtElapsedMillis = clock.elapsedRealtimeMillis(),
                observedAtWallMillis = clock.currentTimeMillis(),
                connectionGeneration = generation,
                batteryCritical = false,
            ),
        )
        return shadow.rawTimestamp != null
    }

    private fun shadowVersion(lockId: LockId): ShadowVersion = shadowVersions.getOrPut(lockId) { ShadowVersion() }

    private fun store(lockId: LockId): LockSnapshotStore = stores.getOrPut(lockId) {
        LockSnapshotStore(LockSnapshot.initial(lockId, LockRoute.CLOUD, pushesUpdatesWhileConnected = false))
    }

    private fun deviceUrl(credential: LockCredential.Cloud, suffix: String): URL? {
        val uuid = runCatching { UUID.fromString(credential.deviceUuid) }.getOrNull() ?: return null
        return URI.create("$BASE_URL${uuid.toString().uppercase(Locale.ROOT)}$suffix").toURL()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length != SECRET_KEY_HEX_LENGTH) return null
        return runCatching { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    internal companion object {
        private const val BASE_URL = "https://app.candyhouse.co/api/sesame2/"
        private const val UNVERIFIABLE_GENERATION = Long.MAX_VALUE
        private const val SECRET_KEY_HEX_LENGTH = 32
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * クラウドの Shadow は実機より遅れることがあるため、`unlocked` / `moved` はどちらも解錠確認済みとは扱わない。
         */
        internal fun parseShadow(body: String): Shadow? {
            val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val status = (obj["CHSesame2Status"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val state = when (status) {
                "locked" -> MechanicalState.LOCKED
                "unlocked", "moved" -> MechanicalState.NOT_LOCKED_OR_MOVING
                else -> return null
            }
            val rawTimestamp = (obj["timestamp"] as? JsonPrimitive)?.takeIf { !it.isString }?.content
            return Shadow(state, rawTimestamp)
        }
    }
}

internal data class Shadow(val state: MechanicalState, val rawTimestamp: String?)

private class ShadowVersion {
    private var version = 1L
    private var lastRawTimestamp: String? = null

    fun current(): Long = synchronized(this) { version }

    fun advanceIfChanged(rawTimestamp: String?): Long = synchronized(this) {
        if (rawTimestamp != null && rawTimestamp != lastRawTimestamp) {
            version += 1
            lastRawTimestamp = rawTimestamp
        }
        version
    }
}
