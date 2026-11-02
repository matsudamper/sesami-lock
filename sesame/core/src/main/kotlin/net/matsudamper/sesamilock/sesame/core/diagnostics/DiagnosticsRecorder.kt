package net.matsudamper.sesamilock.sesame.core.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.matsudamper.sesamilock.sesame.core.model.LockClock
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RequestId

public class DiagnosticsRecorder(
    private val clock: LockClock,
    private val capacity: Int,
) {
    private val state = MutableStateFlow<List<DiagnosticRecord>>(listOf())
    private val startElapsedByRequest = mutableMapOf<RequestId, Long>()
    public val records: StateFlow<List<DiagnosticRecord>> = state.asStateFlow()

    public fun start(requestId: RequestId, lockId: LockId, route: LockRoute, intent: LockIntent) {
        val now = clock.elapsedRealtimeMillis()
        synchronized(startElapsedByRequest) {
            startElapsedByRequest[requestId] = now
        }
        val record = DiagnosticRecord(
            requestId = requestId,
            lockIdHash = lockId.shortHash(),
            route = route,
            intent = intent,
            startedAtWallMillis = clock.currentTimeMillis(),
            elapsedMillisByPoint = mapOf(DiagnosticPoint.TAP to 0L),
            dispatchResult = null,
            outcome = null,
            error = null,
        )
        state.update { (listOf(record) + it).take(capacity) }
    }

    public fun mark(requestId: RequestId, point: DiagnosticPoint) {
        val start = synchronized(startElapsedByRequest) { startElapsedByRequest[requestId] } ?: return
        val offset = clock.elapsedRealtimeMillis() - start
        updateRecord(requestId) { record ->
            if (point in record.elapsedMillisByPoint) {
                record
            } else {
                record.copy(elapsedMillisByPoint = record.elapsedMillisByPoint + (point to offset))
            }
        }
    }

    public fun recordDispatch(requestId: RequestId, dispatchResult: String) {
        updateRecord(requestId) { it.copy(dispatchResult = dispatchResult) }
    }

    public fun finish(requestId: RequestId, outcome: String, error: String?) {
        updateRecord(requestId) { it.copy(outcome = outcome, error = error) }
        synchronized(startElapsedByRequest) {
            startElapsedByRequest.remove(requestId)
        }
    }

    public fun exportJson(): String {
        val array = buildJsonArray {
            records.value.forEach { record ->
                add(
                    buildJsonObject {
                        put("requestId", record.requestId.value)
                        put("lock", record.lockIdHash)
                        put("route", record.route.name)
                        put("command", record.intent.name)
                        put("startedAtEpochMillis", record.startedAtWallMillis)
                        putJsonObject("elapsedMillis") {
                            record.elapsedMillisByPoint.forEach { (point, value) -> put(point.key, value) }
                        }
                        put("dispatchResult", JsonPrimitive(record.dispatchResult))
                        put("outcome", JsonPrimitive(record.outcome))
                        put("error", JsonPrimitive(record.error))
                    },
                )
            }
        }
        return prettyJson.encodeToString(JsonArray.serializer(), array)
    }

    private fun updateRecord(requestId: RequestId, transform: (DiagnosticRecord) -> DiagnosticRecord) {
        state.update { records ->
            records.map { if (it.requestId == requestId) transform(it) else it }
        }
    }

    private companion object {
        private val prettyJson = Json { prettyPrint = true }
    }
}
