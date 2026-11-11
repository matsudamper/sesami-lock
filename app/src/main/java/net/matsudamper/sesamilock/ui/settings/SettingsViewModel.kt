package net.matsudamper.sesamilock.ui.settings

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.NfcAvailability
import net.matsudamper.sesamilock.NfcOpenAppTagWriter
import net.matsudamper.sesamilock.NfcWriteResult

internal class SettingsViewModel(
    private val nfcTagWriter: NfcOpenAppTagWriter,
) : ViewModel() {
    private val nfcWriteState = MutableStateFlow<NfcWriteState>(NfcWriteState.Idle)
    private var writeJob: Job? = null

    private val listener = object : SettingsUiState.Listener {
        override fun onWriteNfcTagClick() {
            nfcWriteState.value = when (nfcTagWriter.availability()) {
                NfcAvailability.NotSupported -> NfcWriteState.Unavailable("この端末は NFC に対応していません")
                NfcAvailability.Disabled -> NfcWriteState.Unavailable("NFC がオフになっています。端末の設定で NFC をオンにしてください")
                NfcAvailability.Ready -> NfcWriteState.WaitingTag
            }
        }

        override fun onNfcTagDiscovered(tag: Tag) {
            if (nfcWriteState.value != NfcWriteState.WaitingTag) return
            nfcWriteState.value = NfcWriteState.Writing
            writeJob = viewModelScope.launch {
                nfcWriteState.value = NfcWriteState.Finished(nfcTagWriter.write(tag))
            }
        }

        override fun onNfcWriteDialogDismiss() {
            writeJob?.cancel()
            writeJob = null
            nfcWriteState.value = NfcWriteState.Idle
        }
    }

    val uiState: StateFlow<SettingsUiState> = nfcWriteState.map(::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(nfcWriteState.value),
    )

    private fun buildUiState(state: NfcWriteState): SettingsUiState = SettingsUiState(
        nfcWriteDialog = buildNfcWriteDialog(state),
        nfcReaderModeEnabled = state == NfcWriteState.WaitingTag || state == NfcWriteState.Writing,
        listener = listener,
    )

    private fun buildNfcWriteDialog(state: NfcWriteState): SettingsUiState.NfcWriteDialog? = when (state) {
        NfcWriteState.Idle -> null

        is NfcWriteState.Unavailable -> SettingsUiState.NfcWriteDialog(
            title = "NFC を使えません",
            message = state.message,
            progressVisible = false,
            dismissButtonText = "閉じる",
        )

        NfcWriteState.WaitingTag -> SettingsUiState.NfcWriteDialog(
            title = "NFC タグに書き込む",
            message = "書き込む NFC タグを端末の背面にかざしてください",
            progressVisible = false,
            dismissButtonText = "キャンセル",
        )

        NfcWriteState.Writing -> SettingsUiState.NfcWriteDialog(
            title = "NFC タグに書き込む",
            message = "書き込み中です。タグを離さないでください",
            progressVisible = true,
            dismissButtonText = "キャンセル",
        )

        is NfcWriteState.Finished -> SettingsUiState.NfcWriteDialog(
            title = if (state.result == NfcWriteResult.Success) "書き込みました" else "書き込めませんでした",
            message = writeResultMessage(state.result),
            progressVisible = false,
            dismissButtonText = "閉じる",
        )
    }

    private fun writeResultMessage(result: NfcWriteResult): String = when (result) {
        NfcWriteResult.Success -> "このタグをかざすとアプリが開きます"
        NfcWriteResult.ReadOnly -> "このタグは書き込み禁止になっています"
        NfcWriteResult.CapacityTooSmall -> "このタグは容量が足りません"
        NfcWriteResult.Unsupported -> "このタグは NDEF の書き込みに対応していません"
        NfcWriteResult.Failed -> "タグとの通信に失敗しました。もう一度お試しください"
    }

    private sealed interface NfcWriteState {
        data object Idle : NfcWriteState

        data class Unavailable(val message: String) : NfcWriteState

        data object WaitingTag : NfcWriteState

        data object Writing : NfcWriteState

        data class Finished(val result: NfcWriteResult) : NfcWriteState
    }
}
