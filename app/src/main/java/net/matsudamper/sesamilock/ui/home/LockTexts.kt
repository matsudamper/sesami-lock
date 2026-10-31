package net.matsudamper.sesamilock.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.matsudamper.sesamilock.sesame.core.coordinator.AbortReason
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandOutcome
import net.matsudamper.sesamilock.sesame.core.coordinator.CommandPhase
import net.matsudamper.sesamilock.sesame.core.coordinator.RejectReason
import net.matsudamper.sesamilock.sesame.core.model.LockDisplayState
import net.matsudamper.sesamilock.sesame.core.model.LockIntent
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState
import net.matsudamper.sesamilock.sesame.core.transport.DispatchResult
import net.matsudamper.sesamilock.sesame.core.transport.TransportError

internal object LockTexts {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun route(route: LockRoute): String = when (route) {
        LockRoute.BLE -> "BLE"
        LockRoute.CLOUD -> "クラウド"
    }

    fun time(wallMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(wallMillis))

    fun displayState(state: LockDisplayState, previous: MechanicalState?): String = when (state) {
        LockDisplayState.LOCKED_VERIFIED -> "施錠（確認済み）"
        LockDisplayState.UNLOCKED_VERIFIED -> "解錠（確認済み）"
        LockDisplayState.NOT_LOCKED_OR_MOVING -> "施錠範囲外 / 動作中"
        LockDisplayState.STALE -> "状態不明（以前の観測: ${previous?.let(::mechanical) ?: "なし"}）"
        LockDisplayState.UNKNOWN -> "状態不明"
        LockDisplayState.DISCONNECTED -> "接続なし"
    }

    fun displayTone(state: LockDisplayState): HomeUiState.Tone = when (state) {
        LockDisplayState.LOCKED_VERIFIED -> HomeUiState.Tone.LOCKED

        LockDisplayState.UNLOCKED_VERIFIED -> HomeUiState.Tone.UNLOCKED

        LockDisplayState.NOT_LOCKED_OR_MOVING -> HomeUiState.Tone.UNLOCKED

        LockDisplayState.STALE,
        LockDisplayState.UNKNOWN,
        LockDisplayState.DISCONNECTED,
        -> HomeUiState.Tone.NEUTRAL
    }

    fun inProgress(phase: CommandPhase.InProgress): String = when (phase) {
        is CommandPhase.AwaitingConfirmation -> "解錠の確認待ち"

        is CommandPhase.AwaitingAuthentication -> "端末認証待ち"

        is CommandPhase.Connecting -> "${intent(phase.intent)}: 接続中"

        is CommandPhase.Sending -> "${intent(phase.intent)}: 送信中"

        is CommandPhase.AwaitingStateVerification -> when (phase.dispatchResult) {
            DispatchResult.Accepted -> "${intent(phase.intent)}: 命令受付済み・動作確認中"
            else -> "${intent(phase.intent)}: 応答なし・状態を確認中"
        }
    }

    fun outcome(intent: LockIntent, outcome: CommandOutcome): Pair<String, HomeUiState.Tone> = when (outcome) {
        CommandOutcome.Verified -> when (intent) {
            LockIntent.LOCK -> "施錠しました（確認済み）" to HomeUiState.Tone.LOCKED
            LockIntent.UNLOCK -> "解錠しました（確認済み）" to HomeUiState.Tone.UNLOCKED
        }

        is CommandOutcome.SentButNotVerified -> when (intent) {
            LockIntent.LOCK -> "施錠できたか不明です。ドアを確認してください（${dispatch(outcome.dispatchResult)}）" to HomeUiState.Tone.WARNING

            LockIntent.UNLOCK ->
                "解錠命令は送信済みですが、解錠を確認できません。鍵が開いている可能性があります。ドアを確認してください（${dispatch(outcome.dispatchResult)}）" to
                    HomeUiState.Tone.WARNING
        }

        is CommandOutcome.NotSent -> "${intent(intent)}は送信していません: ${abort(outcome.reason, outcome.error)}" to HomeUiState.Tone.NEUTRAL
    }

    fun rejected(reason: RejectReason): String = when (reason) {
        RejectReason.OPERATION_IN_PROGRESS -> "前の操作が終わるまで新しい操作はできません"
        RejectReason.ROUTE_UNAVAILABLE -> "この通信経路は利用できません"
    }

    fun mechanical(state: MechanicalState): String = when (state) {
        MechanicalState.LOCKED -> "施錠"
        MechanicalState.UNLOCKED_VERIFIED -> "解錠"
        MechanicalState.NOT_LOCKED_OR_MOVING -> "施錠範囲外"
    }

    fun error(error: TransportError): String = when (error) {
        TransportError.PERMISSION_DENIED -> "Bluetooth / 位置情報の権限がありません"
        TransportError.BLUETOOTH_OFF -> "Bluetooth がオフです"
        TransportError.DEVICE_NOT_REGISTERED -> "鍵が登録されていません"
        TransportError.UNSUPPORTED_DEVICE -> "非対応の機器です"
        TransportError.AUTHENTICATION_FAILED -> "機器の認証に失敗しました"
        TransportError.SERVER_AUTHORIZATION_REQUIRED -> "サーバー署名が必要なゲスト鍵は使えません"
        TransportError.NOT_CONNECTED -> "接続が切れています"
        TransportError.TIMEOUT -> "タイムアウト"
        TransportError.NETWORK -> "ネットワークエラー"
        TransportError.HTTP_CLIENT_ERROR -> "リクエストが拒否されました"
        TransportError.HTTP_SERVER_ERROR -> "サーバーエラー"
        TransportError.INVALID_RESPONSE -> "不正な応答"
        TransportError.DEVICE_ERROR -> "機器エラー"
        TransportError.UNKNOWN -> "不明なエラー"
    }

    private fun intent(intent: LockIntent): String = when (intent) {
        LockIntent.LOCK -> "施錠"
        LockIntent.UNLOCK -> "解錠"
    }

    private fun dispatch(result: DispatchResult): String = when (result) {
        DispatchResult.Accepted -> "命令は受け付けられました"
        is DispatchResult.RejectedByDevice -> "機器がエラーを返しました: ${error(result.error)}"
        is DispatchResult.OutcomeUnknown -> "結果不明: ${error(result.error)}"
        is DispatchResult.NotSent -> "未送信: ${error(result.error)}"
    }

    private fun abort(reason: AbortReason, error: TransportError?): String = when (reason) {
        AbortReason.CANCELLED -> "キャンセルしました"
        AbortReason.CONFIRMATION_EXPIRED -> "確認の期限が切れました"
        AbortReason.AUTHENTICATION_FAILED -> "端末認証が完了しませんでした"
        AbortReason.SEND_DEADLINE_EXPIRED -> "期限内に接続できませんでした。もう一度操作してください"
        AbortReason.CONNECTION_FAILED -> "接続できませんでした（${error?.let(::error) ?: "不明"}）"
        AbortReason.APP_NOT_IN_FOREGROUND -> "アプリが前面にありませんでした"
        AbortReason.DEVICE_MISMATCH -> "接続先の機器が一致しませんでした"
        AbortReason.TRANSPORT_NOT_AUTHENTICATED -> "機器との認証が切れていました"
        AbortReason.NOT_CONFIRMED -> "確認が完了していませんでした"
        AbortReason.NOT_AUTHENTICATED -> "端末認証が完了していませんでした"
    }
}
