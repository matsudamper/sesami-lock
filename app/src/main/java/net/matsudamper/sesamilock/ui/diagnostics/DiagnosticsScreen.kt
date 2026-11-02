package net.matsudamper.sesamilock.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.sesamilock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("診断") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
                actions = {
                    uiState.shareText?.let { shareText ->
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND)
                                    .setType("application/json")
                                    .putExtra(Intent.EXTRA_TEXT, shareText)
                                context.startActivity(Intent.createChooser(intent, "診断データを共有"))
                            },
                        ) {
                            Text("共有")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "この端末内だけに記録しています（アプリ終了で消えます）。鍵・API キー・署名・完全な UUID は含みません。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.items) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.timings, style = MaterialTheme.typography.bodySmall)
                            Text(item.result, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
