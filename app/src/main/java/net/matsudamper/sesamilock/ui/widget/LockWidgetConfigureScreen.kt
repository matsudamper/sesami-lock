package net.matsudamper.sesamilock.ui.widget

import androidx.annotation.ColorRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.widget.R as WidgetR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockWidgetConfigureScreen(
    uiState: LockWidgetConfigureUiState,
    onDoneClick: () -> Unit,
    onOpenAppClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("ウィジェットの設定") }) },
        bottomBar = {
            Button(
                onClick = onDoneClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text("完了") }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val content = uiState.content) {
                LockWidgetConfigureUiState.Content.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                )

                LockWidgetConfigureUiState.Content.Empty -> {
                    Text(
                        "鍵が登録されていません。アプリで鍵を登録してください。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    TextButton(
                        onClick = onOpenAppClick,
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                    ) { Text("アプリを開く") }
                }

                is LockWidgetConfigureUiState.Content.Locks -> {
                    SectionTitle("スタイル")
                    content.styleOptions.forEach { option ->
                        StyleOption(option)
                    }
                    SectionTitle("表示する鍵")
                    content.items.forEach { item ->
                        LockItem(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun StyleOption(option: LockWidgetConfigureUiState.StyleOption, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = option.selected,
                role = Role.RadioButton,
                onClick = { option.listener.onClick() },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option.selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(WallpaperSampleBrush, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                WidgetSample(option.preview)
            }
        }
    }
}

@Composable
private fun WidgetSample(preview: LockWidgetConfigureUiState.StylePreview, modifier: Modifier = Modifier) {
    val colors = preview.sampleColors()
    val shape = RoundedCornerShape(16.dp)
    val statusColor = colorResource(colors.status)
    Row(
        modifier = modifier
            .width(180.dp)
            .background(colorResource(colors.background), shape)
            .border(1.dp, colorResource(colors.outline), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("SESAME", color = colorResource(colors.name), fontSize = 13.sp, maxLines = 1)
            Text("施錠中", color = statusColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun LockItem(item: LockWidgetConfigureUiState.LockItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = item.selected,
                role = Role.RadioButton,
                onClick = { item.listener.onClick() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = item.selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                item.routeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class WidgetSampleColors(
    @param:ColorRes val background: Int,
    @param:ColorRes val outline: Int,
    @param:ColorRes val name: Int,
    @param:ColorRes val status: Int,
)

private fun LockWidgetConfigureUiState.StylePreview.sampleColors(): WidgetSampleColors = when (this) {
    LockWidgetConfigureUiState.StylePreview.WHITE_TRANSLUCENT -> WidgetSampleColors(
        background = WidgetR.color.lock_widget_white_background,
        outline = android.R.color.transparent,
        name = WidgetR.color.lock_widget_white_primary_text,
        status = WidgetR.color.lock_widget_white_locked,
    )

    LockWidgetConfigureUiState.StylePreview.BLACK_TRANSLUCENT -> WidgetSampleColors(
        background = WidgetR.color.lock_widget_black_background,
        outline = android.R.color.transparent,
        name = WidgetR.color.lock_widget_black_primary_text,
        status = WidgetR.color.lock_widget_black_locked,
    )

    LockWidgetConfigureUiState.StylePreview.TRANSPARENT -> WidgetSampleColors(
        background = android.R.color.transparent,
        outline = WidgetR.color.lock_widget_transparent_outline,
        name = WidgetR.color.lock_widget_black_primary_text,
        status = WidgetR.color.lock_widget_black_locked,
    )
}

private val WallpaperSampleBrush = Brush.linearGradient(listOf(Color(0xFF4A5A78), Color(0xFFA9826A)))
