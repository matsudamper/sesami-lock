package net.matsudamper.sesamilock.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LockWidgetConfigureScreen(
    uiState: LockWidgetConfigureUiState,
    onCompleted: () -> Unit,
    onOpenAppClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) currentOnCompleted()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "ウィジェットに表示する鍵",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                when (val content = uiState.content) {
                    LockWidgetConfigureUiState.Content.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                    )

                    LockWidgetConfigureUiState.Content.Empty -> {
                        Text(
                            "鍵が登録されていません。アプリで鍵を登録してください。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        TextButton(
                            onClick = onOpenAppClick,
                            modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp),
                        ) { Text("アプリを開く") }
                    }

                    is LockWidgetConfigureUiState.Content.Locks -> content.items.forEach { item ->
                        LockItem(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockItem(item: LockWidgetConfigureUiState.LockItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) { item.listener.onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            item.routeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
