package net.matsudamper.sesamilock.ui.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.matsudamper.sesamilock.ui.home.HomeUiState
import net.matsudamper.sesamilock.ui.home.LockCard
import net.matsudamper.sesamilock.ui.home.LockSnackbarEffect
import net.matsudamper.sesamilock.ui.home.PermissionBanner

@Composable
internal fun LockOverlayScreen(uiState: HomeUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LifecycleResumeEffect(uiState.listener) {
        uiState.listener.onResume()
        onPauseOrDispose { }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LockSnackbarEffect(snackbar = uiState.snackbar, snackbarHostState = snackbarHostState)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.permissionBanner?.let { PermissionBanner(it) }
            when (val content = uiState.content) {
                HomeUiState.Content.Loading -> MessageCard {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                HomeUiState.Content.Empty, is HomeUiState.Content.Unrecoverable -> MessageCard {
                    Text("鍵が見つかりません。アプリで登録を確認してください。", style = MaterialTheme.typography.bodyMedium)
                }

                is HomeUiState.Content.Locks -> content.cards.forEach { card ->
                    LockCard(card = card, lifted = false, menuVisible = false)
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MessageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), content = content)
    }
}
