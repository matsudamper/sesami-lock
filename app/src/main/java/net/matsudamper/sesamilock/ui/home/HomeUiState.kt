package net.matsudamper.sesamilock.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
internal data class HomeUiState(
    val content: Content,
    val permissionBanner: PermissionBanner?,
    val deleteConfirmation: DeleteConfirmation?,
    val exportQr: ExportQr?,
    val snackbar: Snackbar?,
    val pullRefreshIndicatorVisible: Boolean,
    val editScreenRequested: Boolean,
    val listener: Listener,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data class Unrecoverable(val listener: UnrecoverableListener) : Content

        data object Empty : Content

        data class Locks(val cards: List<LockCard>) : Content
    }

    @Immutable
    data class LockCard(
        val name: String,
        val detail: String,
        val statusLabel: String,
        val statusIcon: StatusIcon,
        val statusTone: Tone,
        val progressVisible: Boolean,
        val warningMessage: String?,
        val lockButton: ActionButton,
        val unlockButton: ActionButton,
        val listener: LockCardListener,
    )

    @Immutable
    sealed interface ActionButton {
        data class HoldToRun(val enabled: Boolean) : ActionButton

        data object Cancel : ActionButton
    }

    enum class Tone {
        NEUTRAL,
        LOCKED,
        UNLOCKED,
    }

    enum class StatusIcon {
        LOCKED,
        UNLOCKED,
        MOVING,
        UNKNOWN,
        BLUETOOTH_DISCONNECTED,
        CLOUD_DISCONNECTED,
    }

    @Immutable
    data class PermissionBanner(
        val requiredPermissions: List<String>,
        val listener: PermissionListener,
    )

    @Immutable
    data class Snackbar(
        val message: String,
        val requiresAcknowledgement: Boolean,
        val listener: SnackbarListener,
    )

    @Immutable
    data class DeleteConfirmation(
        val lockName: String,
        val listener: DeleteConfirmationListener,
    )

    @Immutable
    data class ExportQr(
        val lockName: String,
        val image: ImageBitmap,
        val listener: ExportQrListener,
    )

    interface Listener {
        fun onResume()

        fun onPullToRefresh()

        fun onEditScreenOpened()
    }

    interface UnrecoverableListener {
        fun onDeleteAllClick()
    }

    interface LockCardListener {
        fun onLockLongPress()

        fun onLockTap()

        fun onUnlockLongPress()

        fun onUnlockTap()

        fun onCancelClick()

        fun onExportClick()

        fun onEditClick()

        fun onDeleteClick()
    }

    interface SnackbarListener {
        fun onDismissed()
    }

    interface PermissionListener {
        fun onPermissionResult()
    }

    interface ExportQrListener {
        fun onDismiss()
    }

    interface DeleteConfirmationListener {
        fun onConfirmClick()

        fun onCancel()
    }
}
