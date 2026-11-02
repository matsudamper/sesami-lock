package net.matsudamper.sesamilock.sesame.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RequestId

class DiagnosticsRecorderTest {
    private var now = 1_000L
    private val clock = object : LockClock {
        override fun elapsedRealtimeMillis(): Long = now

        override fun currentTimeMillis(): Long = now
    }
    private val deviceUuid = "8c9a7bb0-1b3c-4f43-9a6e-6c3c2f2b5e01"

    @Test
    fun exportContainsTimingsButNotFullDeviceId() {
        val recorder = DiagnosticsRecorder(clock, capacity = 10)
        val requestId = RequestId("r1")
        recorder.start(requestId, requireNotNull(LockId.fromDeviceUuid(deviceUuid)), LockRoute.BLE, LockIntent.LOCK)
        now += 120
        recorder.mark(requestId, DiagnosticPoint.REQUEST_SENT)
        recorder.finish(requestId, "VERIFIED", null)

        val json = recorder.exportJson()

        assertEquals(120L, recorder.records.value.single().elapsedMillisByPoint[DiagnosticPoint.REQUEST_SENT])
        assertFalse(json.contains(deviceUuid, ignoreCase = true))
    }

    @Test
    fun keepsOnlyLatestRecords() {
        val recorder = DiagnosticsRecorder(clock, capacity = 2)
        val lockId = requireNotNull(LockId.fromDeviceUuid(deviceUuid))
        repeat(3) { recorder.start(RequestId("r$it"), lockId, LockRoute.BLE, LockIntent.LOCK) }

        assertEquals(listOf("r2", "r1"), recorder.records.value.map { it.requestId.value })
    }
}
