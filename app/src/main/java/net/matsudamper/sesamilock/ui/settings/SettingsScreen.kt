package net.matsudamper.sesamilock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.matsudamper.sesamilock.R

private const val LatestReleaseUrl = "https://github.com/matsudamper/sesami-lock/releases/latest"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(LatestReleaseUrl) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("最新版をダウンロード", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "GitHub のリリースページを開きます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
