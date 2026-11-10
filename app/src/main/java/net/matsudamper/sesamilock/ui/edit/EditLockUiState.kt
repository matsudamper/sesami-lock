package net.matsudamper.sesamilock.ui.edit

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
internal data class EditLockUiState(
    val content: Content,
    val closeRequested: Boolean,
    val listener: Listener,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data class Form(
            val name: String,
            val nameError: String?,
            val routeLabel: String,
            val bleInput: BleInput?,
            val cloudInput: CloudInput?,
            val generalErrorMessage: String?,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    data class BleInput(
        val keyText: String,
        val keyError: String?,
        val qrReadingProgressVisible: Boolean,
        val qrPickButtonEnabled: Boolean,
    )

    @Immutable
    data class CloudInput(
        val deviceUuid: String,
        val deviceUuidError: String?,
        val apiKey: String,
        val apiKeyError: String?,
        val secretKey: String,
        val secretKeyError: String?,
        val selectedModelLabel: String,
        val models: List<ModelOption>,
    )

    @Immutable
    data class ModelOption(
        val label: String,
        val listener: ModelOptionListener,
    )

    interface Listener {
        fun onNameChange(value: String)

        fun onKeyTextChange(value: String)

        fun onQrImagePicked(uri: Uri)

        fun onQrScanned(text: String)

        fun onDeviceUuidChange(value: String)

        fun onApiKeyChange(value: String)

        fun onSecretKeyChange(value: String)

        fun onSaveClick()

        fun onBackClick()
    }

    interface ModelOptionListener {
        fun onClick()
    }
}
