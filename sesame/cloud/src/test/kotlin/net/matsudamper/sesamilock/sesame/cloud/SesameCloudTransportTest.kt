package net.matsudamper.sesamilock.sesame.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.model.SesameModel
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

class SesameCloudTransportTest {
    private val deviceUuid = "8C9A7BB0-1B3C-4F43-9A6E-6C3C2F2B5E01"
    private val lockId = requireNotNull(LockId.fromDeviceUuid(deviceUuid))
    private val clock = object : LockClock {
        override fun elapsedRealtimeMillis(): Long = 10_000

        override fun currentTimeMillis(): Long = 1_700_000_000_000
    }
    private val credential = LockCredential.Cloud(
        deviceUuid = deviceUuid,
        model = SesameModel.SESAME_5,
        apiKey = "api-key",
        secretKeyHex = "2b7e151628aed2a6abf7158809cf4f3c",
    )

    private class RecordingExecutor(private val result: HttpResult) : HttpExecutor {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResult {
            requests += request
            return result
        }
    }

    private fun transport(executor: HttpExecutor) = SesameCloudTransport(executor, clock, "sesami-lock").also {
        it.register(lockId, credential)
    }

    @Test
    fun lockAndUnlockUseOnlyExplicitCommandCodes() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        val transport = transport(executor)

        transport.dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"))
        transport.dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("2"))

        val codes = executor.requests.map { Json.parseToJsonElement(requireNotNull(it.body)).jsonObject.getValue("cmd").jsonPrimitive.int }
        assertEquals(listOf(82, 83), codes)
        assertEquals(setOf(82, 83), SesameCloudCommand.entries.map { it.code }.toSet())
    }

    @Test
    fun requestGoesOnlyToOfficialHostOverHttps() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        transport(executor).dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"))

        val url = executor.requests.single().url
        assertEquals("https", url.protocol)
        assertEquals("app.candyhouse.co", url.host)
        assertEquals("/api/sesame2/$deviceUuid/cmd", url.path)
        assertFalse(url.toString().contains("api-key"))
    }

    @Test
    fun serverErrorIsReportedAsUnknownWithoutRetry() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(503, ""))

        val result = transport(executor).dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("1"))

        assertEquals(DispatchResult.OutcomeUnknown(TransportError.HTTP_SERVER_ERROR), result)
        assertEquals(1, executor.requests.size)
    }

    @Test
    fun timeoutIsReportedAsUnknownWithoutRetry() = runTest {
        val executor = RecordingExecutor(HttpResult.TimedOut)

        val result = transport(executor).dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("1"))

        assertIs<DispatchResult.OutcomeUnknown>(result)
        assertEquals(1, executor.requests.size)
    }

    @Test
    fun cloudUnlockedIsNeverVerifiedUnlock() {
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, SesameCloudTransport.parseMechanicalState("""{"CHSesame2Status":"unlocked"}"""))
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, SesameCloudTransport.parseMechanicalState("""{"CHSesame2Status":"moved"}"""))
        assertEquals(MechanicalState.LOCKED, SesameCloudTransport.parseMechanicalState("""{"CHSesame2Status":"locked","timestamp":1598523693}"""))
        assertEquals(null, SesameCloudTransport.parseMechanicalState("""{"CHSesame2Status":"toggle"}"""))
    }

    @Test
    fun requestToStringDoesNotLeakApiKey() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        transport(executor).dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"))

        assertFalse(executor.requests.single().toString().contains("api-key"))
    }
}
