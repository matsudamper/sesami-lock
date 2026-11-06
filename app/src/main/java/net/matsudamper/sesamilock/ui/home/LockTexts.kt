package net.matsudamper.sesamilock.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import net.matsudamper.sesamilock.sesame.core.coordinator.AbortReason
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandOutcome
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.RejectReason
import net.matsudamper.sesamilock.sesame.core.model.LockDisplayState
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

internal object LockTexts {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun route(route: LockRoute): String = when (route) {
        LockRoute.BLE -> "Bluetooth"
        LockRoute.CLOUD -> "クラウド"
    }

    fun time(wallMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(wallMillis))

    fun relativeTime(wallMillis: Long, nowWallMillis: Long): String {
        val elapsed = (nowWallMillis - wallMillis).coerceAtLeast(0).milliseconds
        return when {
            elapsed.inWholeMinutes < 1 -> "たった今"
            elapsed.inWholeHours < 1 -> "${elapsed.inWholeMinutes}分前"
            elapsed.inWholeDays < 1 -> "${elapsed.inWholeHours}時間前"
            else -> shortTimeFormatter.format(Instant.ofEpochMilli(wallMillis))
        }
    }

    fun status(state: LockDisplayState, route: LockRoute): String = when (state) {
        LockDisplayState.LOCKED_VERIFIED -> "施錠中"

        LockDisplayState.UNLOCKED_VERIFIED, LockDisplayState.OUT_OF_LOCK_RANGE -> "解錠中"

        LockDisplayState.MOVING -> "動作中"

        LockDisplayState.UNKNOWN -> "状態を確認しています"

        LockDisplayState.STALE -> when (route) {
            LockRoute.BLE -> "接続していません"
            LockRoute.CLOUD -> "最新の状態ではありません"
        }

        LockDisplayState.DISCONNECTED -> "接続していません"
    }

    fun statusIcon(state: LockDisplayState, route: LockRoute): HomeUiState.StatusIcon = when (state) {
        LockDisplayState.LOCKED_VERIFIED -> HomeUiState.StatusIcon.LOCKED

        LockDisplayState.UNLOCKED_VERIFIED, LockDisplayState.OUT_OF_LOCK_RANGE -> HomeUiState.StatusIcon.UNLOCKED

        LockDisplayState.MOVING -> HomeUiState.StatusIcon.MOVING

        LockDisplayState.UNKNOWN -> HomeUiState.StatusIcon.UNKNOWN

        LockDisplayState.STALE -> when (route) {
            LockRoute.BLE -> HomeUiState.StatusIcon.BLUETOOTH_DISCONNECTED
            LockRoute.CLOUD -> HomeUiState.StatusIcon.UNKNOWN
        }

        LockDisplayState.DISCONNECTED -> disconnectedIcon(route)
    }

    fun disconnectedIcon(route: LockRoute): HomeUiState.StatusIcon = when (route) {
        LockRoute.BLE -> HomeUiState.StatusIcon.BLUETOOTH_DISCONNECTED
        LockRoute.CLOUD -> HomeUiState.StatusIcon.CLOUD_DISCONNECTED
    }

    fun inProgress(phase: CommandPhase.InProgress): String = when (phase) {
        is CommandPhase.Connecting -> "接続しています…"

        is CommandPhase.Sending, is CommandPhase.AwaitingStateVerification -> when (phase.intent) {
            LockIntent.LOCK -> "施錠しています…"
            LockIntent.UNLOCK -> "解錠しています…"
        }
    }

    fun previousState(state: MechanicalState): String = when (state) {
        MechanicalState.LOCKED -> "施錠中"
        MechanicalState.UNLOCKED_VERIFIED, MechanicalState.OUT_OF_LOCK_RANGE -> "解錠中"
        MechanicalState.MOVING -> "動作中"
    }

    fun outcome(intent: LockIntent, outcome: CommandOutcome): OutcomeText = when (outcome) {
        CommandOutcome.Verified -> when (intent) {
            LockIntent.LOCK -> OutcomeText("施錠しました", requiresAcknowledgement = false)
            LockIntent.UNLOCK -> OutcomeText("解錠しました", requiresAcknowledgement = false)
        }

        is CommandOutcome.SentButNotVerified -> when (intent) {
            LockIntent.LOCK -> OutcomeText("施錠を確認できませんでした。ドアの状態を確認してください", requiresAcknowledgement = true)
            LockIntent.UNLOCK -> OutcomeText("解錠を確認できませんでした。ドアの状態を確認してください", requiresAcknowledgement = true)
        }

        is CommandOutcome.NotSent -> when (intent) {
            LockIntent.LOCK -> OutcomeText("施錠できませんでした。${abort(outcome.reason, outcome.error)}", requiresAcknowledgement = false)
            LockIntent.UNLOCK -> OutcomeText("解錠できませんでした。${abort(outcome.reason, outcome.error)}", requiresAcknowledgement = false)
        }
    }

    fun rejected(reason: RejectReason): String = when (reason) {
        RejectReason.OPERATION_IN_PROGRESS -> "ほかの操作が終わるまでお待ちください"
        RejectReason.ROUTE_UNAVAILABLE -> "この鍵は今は操作できません"
    }

    fun error(error: TransportError): String = when (error) {
        TransportError.PERMISSION_DENIED -> "Bluetooth の利用が許可されていません"
        TransportError.BLUETOOTH_OFF -> "Bluetooth をオンにしてください"
        TransportError.DEVICE_NOT_REGISTERED -> "鍵を読み込めていません"
        TransportError.UNSUPPORTED_DEVICE -> "この機種には対応していません"
        TransportError.AUTHENTICATION_FAILED -> "鍵が正しくない可能性があります"
        TransportError.SERVER_AUTHORIZATION_REQUIRED -> "この鍵は使えません。オーナーかマネージャーの鍵を登録してください"
        TransportError.NOT_CONNECTED -> "接続が切れました"
        TransportError.TIMEOUT -> "応答がありませんでした"
        TransportError.NETWORK -> "インターネットに接続できませんでした"
        TransportError.HTTP_CLIENT_ERROR -> "API キーか秘密鍵が正しくない可能性があります"
        TransportError.HTTP_SERVER_ERROR -> "サーバーでエラーが起きました"
        TransportError.INVALID_RESPONSE -> "サーバーから想定外の応答がありました"
        TransportError.DEVICE_ERROR -> "SESAME がエラーを返しました"
        TransportError.UNKNOWN -> "時間をおいてもう一度お試しください"
    }

    private fun abort(reason: AbortReason, error: TransportError?): String = when (reason) {
        AbortReason.CANCELLED -> "操作を取り消しました"
        AbortReason.SEND_DEADLINE_EXPIRED -> "SESAME が見つかりませんでした。近くで操作してください"
        AbortReason.CONNECTION_FAILED -> error?.let(::error) ?: "接続できませんでした"
        AbortReason.APP_NOT_IN_FOREGROUND -> "アプリを閉じたため中止しました"
        AbortReason.DEVICE_MISMATCH -> "別の SESAME に接続されました"
        AbortReason.TRANSPORT_NOT_AUTHENTICATED -> "接続が切れました"
    }
}

internal data class OutcomeText(val message: String, val requiresAcknowledgement: Boolean)
