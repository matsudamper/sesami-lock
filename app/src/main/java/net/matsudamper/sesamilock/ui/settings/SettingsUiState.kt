package net.matsudamper.sesamilock.ui.settings

import android.nfc.Tag
import androidx.compose.runtime.Immutable

@Immutable
internal data class SettingsUiState(
    val nfcWriteDialog: NfcWriteDialog?,
    val nfcReaderModeEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    data class NfcWriteDialog(
        val title: String,
        val message: String,
        val progressVisible: Boolean,
        val dismissButtonText: String,
    )

    interface Listener {
        fun onWriteNfcTagClick()

        fun onNfcTagDiscovered(tag: Tag)

        fun onNfcWriteDialogDismiss()
    }
}
