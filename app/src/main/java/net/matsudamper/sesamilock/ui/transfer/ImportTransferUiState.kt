package net.matsudamper.sesamilock.ui.transfer

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
internal data class ImportTransferUiState(
    val preview: Preview?,
    val errorMessage: String?,
    val readProgressVisible: Boolean,
    val readButtonEnabled: Boolean,
    val saveButtonEnabled: Boolean,
    val closeRequested: Boolean,
    val listener: Listener,
) {
    @Immutable
    data class Preview(
        val name: String,
        val routeLabel: String,
        val modelLabel: String,
    )

    interface Listener {
        fun onQrImagePicked(uri: Uri)

        fun onNameChange(value: String)

        fun onSaveClick()

        fun onBackClick()
    }
}
