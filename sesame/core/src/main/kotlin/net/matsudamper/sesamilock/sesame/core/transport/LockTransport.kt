package net.matsudamper.sesamilock.sesame.core.transport

import kotlinx.coroutines.flow.StateFlow
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.LockSnapshot
import net.matsudamper.sesamilock.sesame.core.model.RequestId

public interface LockTransport {
    public val route: LockRoute

    public fun snapshot(lockId: LockId): StateFlow<LockSnapshot>

    public suspend fun ensureConnected(lockId: LockId): ConnectionResult

    public fun isAuthenticated(lockId: LockId): Boolean

    /**
     * [intent] をそのまま 1 回だけ機器へ送る。状態による操作の読み替えや再送はしない。
     * 接続待ちなどを終えて実際に命令を送り出す直前に [sendGate] を呼び、false なら送らずに [DispatchResult.NotSent] を返す。
     * 呼び出し側の coroutine がキャンセルされても、送信済みの命令は取り消されない。
     */
    public suspend fun dispatchExplicit(lockId: LockId, intent: LockIntent, requestId: RequestId, sendGate: SendGate): DispatchResult

    public suspend fun refreshState(lockId: LockId)
}

public fun interface SendGate {
    public fun tryCommit(): Boolean
}

public sealed interface ConnectionResult {
    public data class Ready(val lockId: LockId, val connectionGeneration: Long) : ConnectionResult

    public data class Failed(val error: TransportError) : ConnectionResult
}

public sealed interface DispatchResult {
    public data object Accepted : DispatchResult

    public data class RejectedByDevice(val error: TransportError) : DispatchResult

    public data class OutcomeUnknown(val error: TransportError) : DispatchResult

    public data class NotSent(val error: TransportError) : DispatchResult
}

public enum class TransportError {
    PERMISSION_DENIED,
    BLUETOOTH_OFF,
    DEVICE_NOT_REGISTERED,
    UNSUPPORTED_DEVICE,
    AUTHENTICATION_FAILED,
    SERVER_AUTHORIZATION_REQUIRED,
    NOT_CONNECTED,
    TIMEOUT,
    NETWORK,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    INVALID_RESPONSE,
    DEVICE_ERROR,
    UNKNOWN,
}
