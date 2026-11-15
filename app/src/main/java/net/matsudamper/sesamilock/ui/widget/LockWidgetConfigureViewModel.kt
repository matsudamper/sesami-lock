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
import net.matsudamper.sesamilock.widget.LockWidgetSetting
import net.matsudamper.sesamilock.widget.LockWidgetStyle

internal class LockWidgetConfigureViewModel(
    repository: EncryptedLockRepository,
    private val widgetController: LockWidgetController,
    private val appWidgetId: Int,
) : ViewModel() {
    private val assignment = MutableStateFlow(Assignment.NOT_STARTED)
    private val storedSettingIfLoaded = MutableStateFlow<LockWidgetSetting?>(null)
    private val styleIfSelected = MutableStateFlow<LockWidgetStyle?>(null)

    val uiState: StateFlow<LockWidgetConfigureUiState> = combine(
        repository.state,
        assignment,
        storedSettingIfLoaded,
        styleIfSelected,
        ::buildUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(repository.state.value, assignment.value, storedSettingIfLoaded.value, styleIfSelected.value),
    )

    init {
        viewModelScope.launch {
            storedSettingIfLoaded.value = widgetController.currentSetting(appWidgetId)
        }
    }

    private fun buildUiState(
        state: LockRepositoryState,
        assignment: Assignment,
        storedSettingIfLoaded: LockWidgetSetting?,
        styleIfSelected: LockWidgetStyle?,
    ): LockWidgetConfigureUiState = LockWidgetConfigureUiState(
        content = when (state) {
            LockRepositoryState.Loading -> LockWidgetConfigureUiState.Content.Loading

            LockRepositoryState.Unrecoverable -> LockWidgetConfigureUiState.Content.Empty

            is LockRepositoryState.Loaded -> if (state.locks.isEmpty()) {
                LockWidgetConfigureUiState.Content.Empty
            } else if (storedSettingIfLoaded == null) {
                LockWidgetConfigureUiState.Content.Loading
            } else {
                val style = styleIfSelected ?: storedSettingIfLoaded.style
                val selectable = assignment == Assignment.NOT_STARTED
                LockWidgetConfigureUiState.Content.Locks(
                    styleOptions = LockWidgetStyle.entries.map { option ->
                        LockWidgetConfigureUiState.StyleOption(
                            label = when (option) {
                                LockWidgetStyle.WHITE_TRANSLUCENT -> "白（半透明）"
                                LockWidgetStyle.BLACK_TRANSLUCENT -> "黒（半透明）"
                                LockWidgetStyle.TRANSPARENT -> "透明"
                            },
                            selected = option == style,
                            enabled = selectable,
                            listener = styleListener(option),
                        )
                    },
                    items = state.locks.map { lock ->
                        LockWidgetConfigureUiState.LockItem(
                            name = lock.displayName,
                            routeLabel = LockTexts.route(lock.route),
                            assignedLabelVisible = lock.id.value == storedSettingIfLoaded.lockKeyIfAssigned,
                            enabled = selectable,
                            listener = itemListener(lock, style),
                        )
                    },
                )
            }
        },
        completed = assignment == Assignment.COMPLETED,
    )

    private fun styleListener(style: LockWidgetStyle) = object : LockWidgetConfigureUiState.StyleOption.Listener {
        override fun onClick() {
            styleIfSelected.value = style
        }
    }

    private fun itemListener(lock: RegisteredLock, style: LockWidgetStyle) = object : LockWidgetConfigureUiState.LockItem.Listener {
        override fun onClick() {
            if (!assignment.compareAndSet(Assignment.NOT_STARTED, Assignment.IN_PROGRESS)) return
            viewModelScope.launch {
                widgetController.assign(appWidgetId, lock.id.value, style).join()
                assignment.update { Assignment.COMPLETED }
            }
        }
    }

    private enum class Assignment {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
    }
}
