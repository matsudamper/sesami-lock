package net.matsudamper.sesamilock.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.ui.component.HoldToActivateButton

private const val UnlockHoldMillis = 600
private val lockedColor = Color(0xFF1B5E20)
private val unlockedColor = Color(0xFFB71C1C)
private val warningColor = Color(0xFFE65100)
private val actionButtonHeight = 64.dp

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAddLockClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SESAME",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDiagnosticsClick) { Text("診断") }
                TextButton(onClick = onAddLockClick) { Text("鍵を追加") }
            }
            uiState.permissionBanner?.let { PermissionBanner(it) }
            when (val content = uiState.content) {
                HomeUiState.Content.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                HomeUiState.Content.Empty -> EmptyContent(onAddLockClick = onAddLockClick)

                is HomeUiState.Content.Unrecoverable -> UnrecoverableContent(content)

                is HomeUiState.Content.Locks -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(content.cards) { card -> LockCard(card) }
                }
            }
        }
    }

    uiState.deleteConfirmation?.let { DeleteConfirmationDialog(it) }
}

@Composable
private fun PermissionBanner(banner: HomeUiState.PermissionBanner, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        banner.listener.onPermissionResult()
    }
    Card(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BLE で接続するには「付近のデバイス」と「位置情報」の許可が必要です。許可がない間は施錠・解錠を送信しません。")
            Button(onClick = { launcher.launch(banner.requiredPermissions.toTypedArray()) }) {
                Text("許可する")
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
                Button(
                    onClick = {
                        confirmVisible = false
                        content.listener.onDeleteAllClick()
                    },
                ) {
                    Text("消去")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmVisible = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun LockCard(card: HomeUiState.LockCard, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                    Text(card.description, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { card.listener.onRefreshClick() }) {
                    Icon(painter = painterResource(R.drawable.ic_refresh), contentDescription = "更新")
                }
                LockCardMenu(onDeleteClick = { card.listener.onDeleteClick() })
            }
            Text(
                text = card.stateLabel,
                color = card.stateTone.color(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(card.observedAtLabel, style = MaterialTheme.typography.bodySmall)
            if (card.batteryWarningVisible) {
                Text("電池残量が少なくなっています", color = warningColor)
            }
            if (card.registrationErrorVisible) {
                Text("この鍵は BLE に登録できませんでした。鍵情報を確認してください", color = warningColor)
            }
            card.progressLabel?.let { label ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { card.listener.onLockClick() },
                    enabled = card.lockButtonEnabled,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = lockedColor, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = actionButtonHeight),
                ) {
                    Text("施錠", style = MaterialTheme.typography.titleLarge)
                }
                HoldToActivateButton(
                    text = "解錠",
                    enabled = card.unlockButtonEnabled,
                    holdMillis = UnlockHoldMillis,
                    containerColor = unlockedColor,
                    contentColor = Color.White,
                    minHeight = actionButtonHeight,
                    onActivate = { card.listener.onUnlockLongPress() },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "解錠はボタンを長押しします",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LockCardMenu(onDeleteClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = "その他")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
        title = { Text("「${confirmation.lockName}」の鍵情報を削除しますか？") },
        text = { Text("この端末から鍵情報を削除します。SESAME 本体や公式アプリの鍵は変更しません。") },
        confirmButton = {
            Button(onClick = { confirmation.listener.onConfirmClick() }) { Text("削除") }
        },
        dismissButton = {
            OutlinedButton(onClick = { confirmation.listener.onCancel() }) { Text("キャンセル") }
        },
    )
}

@Composable
private fun HomeUiState.Tone.color(): Color = when (this) {
    HomeUiState.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    HomeUiState.Tone.LOCKED -> lockedColor
    HomeUiState.Tone.UNLOCKED -> unlockedColor
    HomeUiState.Tone.WARNING -> warningColor
}
