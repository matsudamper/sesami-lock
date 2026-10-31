package net.matsudamper.sesamilock.sesame.core.diagnostics

import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RequestId

public enum class DiagnosticPoint(public val key: String) {
    TAP("tap_at"),
    CONFIRMED("confirmed_at"),
    AUTH_SUCCEEDED("auth_succeeded_at"),
    CONNECT_STARTED("connect_started_at"),
    CONNECTION_AUTHENTICATED("authenticated_at"),
    REQUEST_SENT("request_sent_at"),
    ACK("ack_at"),
    MECHANICAL_STATUS("mechanical_status_at"),
    VERIFIED("verified_at"),
}

public data class DiagnosticRecord(
    val requestId: RequestId,
    val lockIdHash: String,
    val route: LockRoute,
    val intent: LockIntent,
    val startedAtWallMillis: Long,
    val elapsedMillisByPoint: Map<DiagnosticPoint, Long>,
    val dispatchResult: String?,
    val outcome: String?,
    val error: String?,
)
