package net.matsudamper.sesamilock.sesame.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
import net.matsudamper.sesamilock.sesame.core.transport.ConnectionResult
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.SendGate
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

        override suspend fun execute(request: HttpRequest, beforeSend: () -> Boolean): HttpResult {
            if (!beforeSend()) return HttpResult.NotSentByGate
            requests += request
            return result
        }
    }

    private val allowSend = SendGate { true }

    private fun transport(executor: HttpExecutor) = SesameCloudTransport(executor, clock, "sesami-lock").also {
        it.register(lockId, credential)
    }

    @Test
    fun lockAndUnlockUseOnlyExplicitCommandCodes() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        val transport = transport(executor)

        transport.dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"), allowSend)
        transport.dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("2"), allowSend)

        val codes = executor.requests.map { Json.parseToJsonElement(requireNotNull(it.body)).jsonObject.getValue("cmd").jsonPrimitive.int }
        assertEquals(listOf(82, 83), codes)
        assertEquals(setOf(82, 83), SesameCloudCommand.entries.map { it.code }.toSet())
    }

    @Test
    fun requestGoesOnlyToOfficialHostOverHttps() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        transport(executor).dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"), allowSend)

        val url = executor.requests.single().url
        assertEquals("https", url.protocol)
        assertEquals("app.candyhouse.co", url.host)
        assertEquals("/api/sesame2/$deviceUuid/cmd", url.path)
        assertFalse(url.toString().contains("api-key"))
    }

    @Test
    fun serverErrorIsReportedAsUnknownWithoutRetry() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(503, ""))

        val result = transport(executor).dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("1"), allowSend)

        assertEquals(DispatchResult.OutcomeUnknown(TransportError.HTTP_SERVER_ERROR), result)
        assertEquals(1, executor.requests.size)
    }

    @Test
    fun timeoutIsReportedAsUnknownWithoutRetry() = runTest {
        val executor = RecordingExecutor(HttpResult.TimedOut)

        val result = transport(executor).dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("1"), allowSend)

        assertIs<DispatchResult.OutcomeUnknown>(result)
        assertEquals(1, executor.requests.size)
    }

    @Test
    fun cloudUnlockedIsNeverVerifiedUnlock() {
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, SesameCloudTransport.parseShadow("""{"CHSesame2Status":"unlocked"}""")?.state)
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, SesameCloudTransport.parseShadow("""{"CHSesame2Status":"moved"}""")?.state)
        assertEquals(MechanicalState.LOCKED, SesameCloudTransport.parseShadow("""{"CHSesame2Status":"locked","timestamp":1598523693}""")?.state)
        assertEquals(null, SesameCloudTransport.parseShadow("""{"CHSesame2Status":"toggle"}""")?.state)
    }

    @Test
    fun requestToStringDoesNotLeakApiKey() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))
        transport(executor).dispatchExplicit(lockId, LockIntent.LOCK, RequestId("1"), allowSend)

        assertFalse(executor.requests.single().toString().contains("api-key"))
    }

    @Test
    fun refusedGateSendsNothing() = runTest {
        val executor = RecordingExecutor(HttpResult.Response(200, ""))

        val result = transport(executor).dispatchExplicit(lockId, LockIntent.UNLOCK, RequestId("1"), SendGate { false })

        assertIs<DispatchResult.NotSent>(result)
        assertEquals(0, executor.requests.size)
    }

    private class ScriptedExecutor(private val bodies: MutableList<String>) : HttpExecutor {
        override suspend fun execute(request: HttpRequest, beforeSend: () -> Boolean): HttpResult {
            if (!beforeSend()) return HttpResult.NotSentByGate
            return HttpResult.Response(200, if (request.method == "GET") bodies.removeAt(0) else "")
        }
    }

    @Test
    fun unchangedShadowDoesNotAdvanceGeneration() = runTest {
        val transport = transport(
            ScriptedExecutor(
                mutableListOf(
                    """{"CHSesame2Status":"locked","timestamp":100}""",
                    """{"CHSesame2Status":"locked","timestamp":100}""",
                    """{"CHSesame2Status":"locked","timestamp":101}""",
                ),
            ),
        )

        val ready = assertIs<ConnectionResult.Ready>(transport.ensureConnected(lockId))
        transport.refreshState(lockId)
        val afterUnchanged = requireNotNull(transport.snapshot(lockId).value.observation).connectionGeneration
        transport.refreshState(lockId)
        val afterChanged = requireNotNull(transport.snapshot(lockId).value.observation).connectionGeneration

        assertEquals(ready.connectionGeneration, afterUnchanged)
        assertTrue(afterChanged > ready.connectionGeneration)
    }

    @Test
    fun shadowWithoutTimestampCannotBeUsedForVerification() = runTest {
        val transport = transport(ScriptedExecutor(mutableListOf("""{"CHSesame2Status":"locked"}""")))

        val ready = assertIs<ConnectionResult.Ready>(transport.ensureConnected(lockId))

        assertEquals(Long.MAX_VALUE, ready.connectionGeneration)
    }
}
