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

internal class LockWidgetConfigureViewModel(
    repository: EncryptedLockRepository,
    private val widgetController: LockWidgetController,
    private val appWidgetId: Int,
) : ViewModel() {
    private val assignment = MutableStateFlow(Assignment.NOT_STARTED)

    val uiState: StateFlow<LockWidgetConfigureUiState> = combine(repository.state, assignment, ::buildUiState).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(repository.state.value, assignment.value),
    )

    private fun buildUiState(state: LockRepositoryState, assignment: Assignment): LockWidgetConfigureUiState = LockWidgetConfigureUiState(
        content = when (state) {
            LockRepositoryState.Loading -> LockWidgetConfigureUiState.Content.Loading

            LockRepositoryState.Unrecoverable -> LockWidgetConfigureUiState.Content.Empty

            is LockRepositoryState.Loaded -> if (state.locks.isEmpty()) {
                LockWidgetConfigureUiState.Content.Empty
            } else {
                LockWidgetConfigureUiState.Content.Locks(
                    state.locks.map { lock ->
                        LockWidgetConfigureUiState.LockItem(
                            name = lock.displayName,
                            routeLabel = LockTexts.route(lock.route),
                            enabled = assignment == Assignment.NOT_STARTED,
                            listener = itemListener(lock),
                        )
                    },
                )
            }
        },
        completed = assignment == Assignment.COMPLETED,
    )

    private fun itemListener(lock: RegisteredLock) = object : LockWidgetConfigureUiState.LockItem.Listener {
        override fun onClick() {
            if (!assignment.compareAndSet(Assignment.NOT_STARTED, Assignment.IN_PROGRESS)) return
            viewModelScope.launch {
                widgetController.assign(appWidgetId, lock.id.value).join()
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
