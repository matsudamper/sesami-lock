package net.matsudamper.sesamilock.ui.widget

import androidx.compose.runtime.Immutable

@Immutable
internal data class LockWidgetConfigureUiState(
    val content: Content,
    val completed: Boolean,
) {
    @Immutable
    sealed interface Content {
        data object Loading : Content

        data object Empty : Content

        data class Locks(
            val styleOptions: List<StyleOption>,
            val items: List<LockItem>,
        ) : Content
    }

    @Immutable
    data class StyleOption(
        val label: String,
        val selected: Boolean,
        val enabled: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }

    @Immutable
    data class LockItem(
        val name: String,
        val routeLabel: String,
        val assignedLabelVisible: Boolean,
        val enabled: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }
}
