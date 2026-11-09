package net.matsudamper.sesamilock.ui.add

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
internal data class AddLockUiState(
    val name: String,
    val bleRouteSelected: Boolean,
    val cloudRouteSelected: Boolean,
    val bleInput: BleInput?,
    val cloudInput: CloudInput?,
    val generalErrorMessage: String?,
    val saveButtonEnabled: Boolean,
    val transferImportProgressVisible: Boolean,
    val transferImportButtonEnabled: Boolean,
    val closeRequested: Boolean,
    val listener: Listener,
) {
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
        val selected: Boolean,
        val listener: ModelOptionListener,
    )

    interface Listener {
        fun onNameChange(value: String)

        fun onBleRouteClick()

        fun onCloudRouteClick()

        fun onKeyTextChange(value: String)

        fun onQrImagePicked(uri: Uri)

        fun onTransferQrImagePicked(uri: Uri)

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
