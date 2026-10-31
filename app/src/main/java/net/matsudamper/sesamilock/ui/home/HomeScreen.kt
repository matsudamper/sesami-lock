package net.matsudamper.sesamilock.ui.home

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.matsudamper.sesamilock.security.UnlockAuthenticationListener
import net.matsudamper.sesamilock.security.UnlockAuthenticator
import net.matsudamper.sesamilock.ui.component.HoldToActivateButton

private const val UnlockHoldMillis = 600
private val lockedColor = Color(0xFF1B5E20)
private val unlockedColor = Color(0xFFB71C1C)
private val warningColor = Color(0xFFE65100)

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
    AuthenticationPromptEffect(uiState.authenticationPrompt)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
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

                HomeUiState.Content.Empty -> Text(
                    text = "鍵が登録されていません。「鍵を追加」から登録してください。",
                    modifier = Modifier.padding(16.dp),
                )

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

    uiState.unlockConfirmation?.let { UnlockConfirmationDialog(it) }
    uiState.deleteConfirmation?.let { DeleteConfirmationDialog(it) }
}

@Composable
private fun AuthenticationPromptEffect(prompt: HomeUiState.AuthenticationPrompt?) {
    val activity = LocalActivity.current as? FragmentActivity
    LaunchedEffect(prompt) {
        if (prompt == null) return@LaunchedEffect
        if (activity == null) {
            prompt.listener.onFailed("端末認証を開始できません")
            return@LaunchedEffect
        }
        UnlockAuthenticator.authenticate(
            activity = activity,
            title = prompt.title,
            subtitle = prompt.subtitle,
            listener = object : UnlockAuthenticationListener {
                override fun onAuthenticationSucceeded() {
                    prompt.listener.onSucceeded()
                }

                override fun onAuthenticationFailed(message: String) {
                    prompt.listener.onFailed(message)
                }
            },
        )
    }
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
private fun UnrecoverableContent(content: HomeUiState.Content.Unrecoverable, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("保存された鍵情報を復元できません。端末の移行やバックアップ復元で暗号鍵が変わった可能性があります。鍵情報を削除して登録し直してください。")
        Button(onClick = { content.listener.onDeleteAllClick() }) {
            Text("保存された鍵情報を削除")
        }
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
                TextButton(onClick = { card.listener.onRefreshClick() }) { Text("更新") }
                TextButton(onClick = { card.listener.onDeleteClick() }) { Text("削除") }
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
            if (card.operationProgressVisible) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            card.operationMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = message, color = card.operationTone.color(), modifier = Modifier.weight(1f))
                    if (card.dismissOperationVisible) {
                        TextButton(onClick = { card.listener.onDismissOperationClick() }) { Text("閉じる") }
                    }
                }
            }
            Button(
                onClick = { card.listener.onLockClick() },
                enabled = card.lockButtonEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = lockedColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
            ) {
                Text("施錠", style = MaterialTheme.typography.headlineSmall)
            }
            HoldToActivateButton(
                text = "解錠（長押し）",
                enabled = card.unlockButtonEnabled,
                holdMillis = UnlockHoldMillis,
                color = unlockedColor,
                onActivate = { card.listener.onUnlockLongPress() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UnlockConfirmationDialog(confirmation: HomeUiState.UnlockConfirmation) {
    AlertDialog(
        onDismissRequest = { confirmation.listener.onCancel() },
        title = { Text("「${confirmation.lockName}」を解錠しますか？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("通信経路: ${confirmation.routeLabel}")
                Text("現在の観測: ${confirmation.observedStateLabel}")
                if (confirmation.stateUnknownWarningVisible) {
                    Text("現在の状態が確認できていません。それでも解錠するか確認してください。", color = warningColor)
                }
                Text("次に端末認証を行います。送信後は取り消せません。")
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmation.listener.onConfirmClick() },
                colors = ButtonDefaults.buttonColors(containerColor = unlockedColor),
            ) {
                Text("解錠する")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { confirmation.listener.onCancel() }) { Text("キャンセル") }
        },
    )
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
