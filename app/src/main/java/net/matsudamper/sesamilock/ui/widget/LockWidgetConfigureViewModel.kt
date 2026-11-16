package net.matsudamper.sesamilock.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.credential.EncryptedLockRepository
import net.matsudamper.sesamilock.sesame.credential.LockRepositoryState
import net.matsudamper.sesamilock.ui.home.LockTexts
import net.matsudamper.sesamilock.widget.LockWidgetController
import net.matsudamper.sesamilock.widget.LockWidgetIcon
import net.matsudamper.sesamilock.widget.LockWidgetSetting
import net.matsudamper.sesamilock.widget.LockWidgetStyle

internal class LockWidgetConfigureViewModel(
    repository: EncryptedLockRepository,
    private val widgetController: LockWidgetController,
    private val appWidgetId: Int,
) : ViewModel() {
    private val settingIfLoaded = MutableStateFlow<LockWidgetSetting?>(null)

    val uiState: StateFlow<LockWidgetConfigureUiState> = combine(repository.state, settingIfLoaded, ::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(repository.state.value, settingIfLoaded.value),
    )

    init {
        viewModelScope.launch {
            settingIfLoaded.value = widgetController.currentSetting(appWidgetId)
            widgetController.render(appWidgetId)
        }
    }

    private fun buildUiState(state: LockRepositoryState, settingIfLoaded: LockWidgetSetting?): LockWidgetConfigureUiState = LockWidgetConfigureUiState(
        content = when (state) {
            LockRepositoryState.Loading -> LockWidgetConfigureUiState.Content.Loading

            LockRepositoryState.Unrecoverable -> LockWidgetConfigureUiState.Content.Empty

            is LockRepositoryState.Loaded -> if (state.locks.isEmpty()) {
                LockWidgetConfigureUiState.Content.Empty
            } else if (settingIfLoaded == null) {
                LockWidgetConfigureUiState.Content.Loading
            } else {
                val style = settingIfLoaded.style
                LockWidgetConfigureUiState.Content.Locks(
                    stylePreview = LockWidgetConfigureUiState.StylePreview(
                        backgroundColorRes = style.backgroundColorRes,
                        outlineColorRes = style.outlineColorRes,
                        nameColorRes = style.primaryTextColorRes,
                        statusColorRes = style.tintColorRes(LockWidgetIcon.LOCKED),
                    ),
                    colorOptions = LockWidgetStyle.Color.entries.map { color ->
                        LockWidgetConfigureUiState.StyleOption(
                            label = when (color) {
                                LockWidgetStyle.Color.WHITE -> "白"
                                LockWidgetStyle.Color.BLACK -> "黒"
                            },
                            selected = color == style.color,
                            listener = styleListener(LockWidgetStyle.of(color, style.opacity)),
                        )
                    },
                    opacityOptions = LockWidgetStyle.Opacity.entries.map { opacity ->
                        LockWidgetConfigureUiState.StyleOption(
                            label = when (opacity) {
                                LockWidgetStyle.Opacity.TRANSPARENT -> "透明"
                                LockWidgetStyle.Opacity.TRANSLUCENT -> "半透明"
                                LockWidgetStyle.Opacity.OPAQUE -> "透過なし"
                            },
                            selected = opacity == style.opacity,
                            listener = styleListener(LockWidgetStyle.of(style.color, opacity)),
                        )
                    },
                    items = state.locks.map { lock ->
                        LockWidgetConfigureUiState.LockItem(
                            name = lock.displayName,
                            routeLabel = LockTexts.route(lock.route),
                            selected = lock.id.value == settingIfLoaded.lockKeyIfAssigned,
                            listener = itemListener(lock),
                        )
                    },
                )
            }
        },
    )

    private fun styleListener(style: LockWidgetStyle) = object : LockWidgetConfigureUiState.StyleOption.Listener {
        override fun onClick() {
            settingIfLoaded.update { it?.copy(style = style) }
            widgetController.applyStyle(appWidgetId, style)
        }
    }

    private fun itemListener(lock: RegisteredLock) = object : LockWidgetConfigureUiState.LockItem.Listener {
        override fun onClick() {
            settingIfLoaded.update { it?.copy(lockKeyIfAssigned = lock.id.value) }
            widgetController.assign(appWidgetId, lock.id.value)
        }
    }
}
