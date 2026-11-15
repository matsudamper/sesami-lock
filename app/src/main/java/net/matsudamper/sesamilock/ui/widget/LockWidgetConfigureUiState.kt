package net.matsudamper.sesamilock.ui.widget

import androidx.compose.runtime.Immutable

@Immutable
internal data class LockWidgetConfigureUiState(
    val content: Content,
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
        val preview: StylePreview,
        val selected: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }

    enum class StylePreview {
        WHITE_TRANSLUCENT,
        BLACK_TRANSLUCENT,
        TRANSPARENT,
    }

    @Immutable
    data class LockItem(
        val name: String,
        val routeLabel: String,
        val selected: Boolean,
        val listener: Listener,
    ) {
        interface Listener {
            fun onClick()
        }
    }
}
