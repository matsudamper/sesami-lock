package net.matsudamper.sesamilock.sesame.core.coordinator

import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.RequestId
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

public sealed interface CommandPhase {
    public data object Idle : CommandPhase

    public sealed interface InProgress : CommandPhase {
        public val requestId: RequestId
        public val intent: LockIntent
    }

    public data class AwaitingConfirmation(override val requestId: RequestId) : InProgress {
        override val intent: LockIntent = LockIntent.UNLOCK
    }

    public data class AwaitingAuthentication(override val requestId: RequestId) : InProgress {
        override val intent: LockIntent = LockIntent.UNLOCK
    }

    public data class Connecting(override val requestId: RequestId, override val intent: LockIntent) : InProgress

    public data class Sending(override val requestId: RequestId, override val intent: LockIntent) : InProgress

    public data class AwaitingStateVerification(
        override val requestId: RequestId,
        override val intent: LockIntent,
        val dispatchResult: DispatchResult,
    ) : InProgress

    public data class Completed(
        val requestId: RequestId,
        val intent: LockIntent,
        val outcome: CommandOutcome,
    ) : CommandPhase
}

public sealed interface CommandOutcome {
    public data object Verified : CommandOutcome

    public data class SentButNotVerified(val dispatchResult: DispatchResult) : CommandOutcome

    public data class NotSent(val reason: AbortReason, val error: TransportError?) : CommandOutcome
}

public enum class AbortReason {
    CANCELLED,
    CONFIRMATION_EXPIRED,
    AUTHENTICATION_FAILED,
    SEND_DEADLINE_EXPIRED,
    CONNECTION_FAILED,
    APP_NOT_IN_FOREGROUND,
    DEVICE_MISMATCH,
    TRANSPORT_NOT_AUTHENTICATED,
    NOT_CONFIRMED,
    NOT_AUTHENTICATED,
}

public sealed interface StartResult {
    public data class Started(val requestId: RequestId) : StartResult

    public data class Rejected(val reason: RejectReason) : StartResult
}

public enum class RejectReason {
    OPERATION_IN_PROGRESS,
    ROUTE_UNAVAILABLE,
}
