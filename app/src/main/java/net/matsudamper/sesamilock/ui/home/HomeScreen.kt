package net.matsudamper.sesamilock.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.ui.component.HoldToActivateButton
import net.matsudamper.sesamilock.ui.theme.lockStatusColors

private const val ActionHoldMillis = 600
private val actionButtonHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAddLockClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(uiState.listener) {
        uiState.listener.onResume()
        onPauseOrDispose { }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbar) {
        val snackbar = uiState.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = snackbar.message,
            withDismissAction = snackbar.requiresAcknowledgement,
            duration = if (snackbar.requiresAcknowledgement) SnackbarDuration.Indefinite else SnackbarDuration.Short,
        )
        snackbar.listener.onDismissed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("SESAME") },
                actions = {
                    IconButton(onClick = onAddLockClick) {
                        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = "鍵を追加")
                    }
                    OverflowMenu(onDiagnosticsClick = onDiagnosticsClick, onSettingsClick = onSettingsClick)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val content = uiState.content) {
                HomeUiState.Content.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                HomeUiState.Content.Empty -> EmptyContent(onAddLockClick = onAddLockClick)

                is HomeUiState.Content.Unrecoverable -> UnrecoverableContent(content)

                is HomeUiState.Content.Locks -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.permissionBanner?.let { banner ->
                        item { PermissionBanner(banner) }
                    }
                    items(content.cards) { card -> LockCard(card) }
                }
            }
        }
    }

    uiState.deleteConfirmation?.let { DeleteConfirmationDialog(it) }
}

@Composable
private fun OverflowMenu(onDiagnosticsClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = "その他")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("診断") },
                onClick = {
                    expanded = false
                    onDiagnosticsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("設定") },
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            )
        }
    }
}

@Composable
private fun PermissionBanner(banner: HomeUiState.PermissionBanner, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        banner.listener.onPermissionResult()
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bluetooth の許可が必要です", style = MaterialTheme.typography.titleMedium)
            Text("近くの SESAME と通信するために使います。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                ) {
                    Text("設定を開く")
                }
                Button(onClick = { launcher.launch(banner.requiredPermissions.toTypedArray()) }) {
                    Text("許可する")
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(onAddLockClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text("鍵がまだありません", style = MaterialTheme.typography.titleLarge)
        Text(
            "公式アプリで共有した鍵を登録すると、ここから施錠・解錠できます。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddLockClick) { Text("鍵を追加") }
    }
}

@Composable
private fun UnrecoverableContent(content: HomeUiState.Content.Unrecoverable, modifier: Modifier = Modifier) {
    var confirmVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("鍵情報を読み込めませんでした", style = MaterialTheme.typography.titleLarge)
        Text(
            "機種変更やバックアップからの復元のあとに起きることがあります。お手数ですが、鍵を登録し直してください。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { confirmVisible = true }) { Text("登録し直す") }
    }
    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text("保存されている鍵情報を消去しますか？") },
            text = { Text("この端末に保存された鍵情報を消去します。SESAME 本体や公式アプリの鍵は変わりません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmVisible = false
                        content.listener.onDeleteAllClick()
                    },
                ) {
                    Text("消去")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun LockCard(card: HomeUiState.LockCard, modifier: Modifier = Modifier) {
    val statusColors = lockStatusColors()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusBadge(icon = card.statusIcon, tone = card.statusTone, progressVisible = card.progressVisible)
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = card.statusLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = card.statusTone.textColor(),
                    )
                    Text(card.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LockCardMenu(
                    onRefreshClick = { card.listener.onRefreshClick() },
                    onEditClick = { card.listener.onEditClick() },
                    onDeleteClick = { card.listener.onDeleteClick() },
                )
            }
            card.warningMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_battery_alert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HoldToActivateButton(
                    text = "施錠",
                    icon = painterResource(R.drawable.ic_lock),
                    enabled = card.lockButtonEnabled,
                    holdMillis = ActionHoldMillis,
                    containerColor = statusColors.lockedContainer,
                    contentColor = statusColors.locked,
                    minHeight = actionButtonHeight,
                    onActivate = card.listener::onLockLongPress,
                    onTapWithoutHold = card.listener::onLockTap,
                    modifier = Modifier.weight(1f),
                )
                HoldToActivateButton(
                    text = "解錠",
                    icon = painterResource(R.drawable.ic_lock_open),
                    enabled = card.unlockButtonEnabled,
                    holdMillis = ActionHoldMillis,
                    containerColor = statusColors.unlockedContainer,
                    contentColor = statusColors.unlocked,
                    minHeight = actionButtonHeight,
                    onActivate = card.listener::onUnlockLongPress,
                    onTapWithoutHold = card.listener::onUnlockTap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(icon: HomeUiState.StatusIcon, tone: HomeUiState.Tone, progressVisible: Boolean, modifier: Modifier = Modifier) {
    val statusColors = lockStatusColors()
    val (container, content) = when (tone) {
        HomeUiState.Tone.LOCKED -> statusColors.lockedContainer to statusColors.locked
        HomeUiState.Tone.UNLOCKED -> statusColors.unlockedContainer to statusColors.unlocked
        HomeUiState.Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    when (icon) {
                        HomeUiState.StatusIcon.LOCKED -> R.drawable.ic_lock
                        HomeUiState.StatusIcon.UNLOCKED -> R.drawable.ic_lock_open
                        HomeUiState.StatusIcon.MOVING -> R.drawable.ic_sync
                        HomeUiState.StatusIcon.UNKNOWN -> R.drawable.ic_question_mark
                        HomeUiState.StatusIcon.DISCONNECTED -> R.drawable.ic_bluetooth_disabled
                    },
                ),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(28.dp),
            )
        }
        if (progressVisible) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp), color = content, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun LockCardMenu(onRefreshClick: () -> Unit, onEditClick: () -> Unit, onDeleteClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = "その他")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("状態を更新") },
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_refresh), contentDescription = null) },
                onClick = {
                    expanded = false
                    onRefreshClick()
                },
            )
            DropdownMenuItem(
                text = { Text("この鍵を編集") },
                onClick = {
                    expanded = false
                    onEditClick()
                },
            )
            DropdownMenuItem(
                text = { Text("この鍵を削除") },
                onClick = {
                    expanded = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(confirmation: HomeUiState.DeleteConfirmation) {
    AlertDialog(
        onDismissRequest = { confirmation.listener.onCancel() },
        title = { Text("「${confirmation.lockName}」を削除しますか？") },
        text = { Text("この端末から鍵を削除します。SESAME 本体や公式アプリの鍵は変わりません。") },
        confirmButton = {
            TextButton(onClick = { confirmation.listener.onConfirmClick() }) { Text("削除") }
        },
        dismissButton = {
            TextButton(onClick = { confirmation.listener.onCancel() }) { Text("キャンセル") }
        },
    )
}

@Composable
private fun HomeUiState.Tone.textColor(): Color {
    val statusColors = lockStatusColors()
    return when (this) {
        HomeUiState.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        HomeUiState.Tone.LOCKED -> statusColors.locked
        HomeUiState.Tone.UNLOCKED -> statusColors.unlocked
    }
}
