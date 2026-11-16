package net.matsudamper.sesamilock.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

internal class LockWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val preferences = currentState<Preferences>()
            val lockKey = preferences[LockWidgetKeys.lockKey]
            val host = context.applicationContext as LockWidgetHost
            val inProgressLabel = preferences[LockWidgetKeys.inProgressLabel]
            val style = preferences.lockWidgetStyle()
            if (lockKey == null) {
                val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
                UnconfiguredContent(
                    style = style,
                    onClick = actionStartActivity(host.createLockSelectionIntent(appWidgetId)),
                )
            } else {
                LockContent(
                    style = style,
                    name = preferences[LockWidgetKeys.name].orEmpty(),
                    statusLabel = inProgressLabel ?: preferences[LockWidgetKeys.statusLabel] ?: "状態不明",
                    detail = preferences[LockWidgetKeys.detail].orEmpty(),
                    icon = if (inProgressLabel != null) {
                        LockWidgetIcon.MOVING
                    } else {
                        preferences[LockWidgetKeys.statusIcon]
                            ?.let { stored -> LockWidgetIcon.entries.firstOrNull { it.name == stored } }
                            ?: LockWidgetIcon.UNKNOWN
                    },
                    onClick = actionStartActivity(host.createLockOverlayIntent(lockKey)),
                )
            }
        }
    }
}

internal class LockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockWidget()
}

@Composable
internal fun UnconfiguredContent(style: LockWidgetStyle, onClick: Action) {
    val size = LocalSize.current
    val compact = size.width < WideSize.width
    val padding = if (compact) CompactPadding else WidePadding
    val contentSize = size.contentSize(padding)
    val text = if (compact) "鍵を選択" else "タップして鍵を選択してください"
    val lineCount = if (compact) 1 else 2
    Column(
        modifier = widgetContainerModifier(padding, style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            maxLines = lineCount,
            style = TextStyle(
                color = ColorProvider(style.primaryTextColorRes),
                fontSize = fittingFontSize(
                    text = text.take((text.length + lineCount - 1) / lineCount),
                    availableWidth = contentSize.width,
                    availableHeight = contentSize.height / (lineCount * LineHeightRatio),
                ),
            ),
        )
    }
}

@Composable
internal fun LockContent(
    style: LockWidgetStyle,
    name: String,
    statusLabel: String,
    detail: String,
    icon: LockWidgetIcon,
    onClick: Action,
) {
    val size = LocalSize.current
    if (size.width < WideSize.width) {
        CompactLockContent(style = style, statusLabel = statusLabel, icon = icon, onClick = onClick)
    } else {
        WideLockContent(
            style = style,
            name = name,
            statusLabel = statusLabel,
            detailIfFits = detail.takeIf { size.height >= WideWithDetailSize.height },
            icon = icon,
            onClick = onClick,
        )
    }
}

@Composable
private fun CompactLockContent(style: LockWidgetStyle, statusLabel: String, icon: LockWidgetIcon, onClick: Action) {
    val tint = icon.tint(style)
    val contentSize = LocalSize.current.contentSize(CompactPadding)
    val iconSize = (contentSize.height * CompactIconHeightRatio).coerceIn(MinIconSize, MaxIconSize)
    val statusSize = fittingFontSize(
        text = statusLabel,
        availableWidth = contentSize.width,
        availableHeight = (contentSize.height - iconSize) / LineHeightRatio,
    )
    Column(
        modifier = widgetContainerModifier(CompactPadding, style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(icon.drawableRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(iconSize),
        )
        Text(
            text = statusLabel,
            maxLines = 1,
            style = TextStyle(color = tint, fontSize = statusSize, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun WideLockContent(
    style: LockWidgetStyle,
    name: String,
    statusLabel: String,
    detailIfFits: String?,
    icon: LockWidgetIcon,
    onClick: Action,
) {
    val tint = icon.tint(style)
    val contentSize = LocalSize.current.contentSize(WidePadding)
    val iconSize = (contentSize.height * WideIconHeightRatio).coerceIn(MinIconSize, MaxIconSize)
    val textWidth = contentSize.width - iconSize - WideIconSpacing
    val lineRatioSum = NameToStatusRatio + 1f + if (detailIfFits == null) 0f else DetailToStatusRatio
    val statusSize = fittingFontSize(
        text = statusLabel,
        availableWidth = textWidth,
        availableHeight = contentSize.height / (lineRatioSum * LineHeightRatio),
    )
    Row(
        modifier = widgetContainerModifier(WidePadding, style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon.drawableRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(iconSize),
        )
        Spacer(modifier = GlanceModifier.width(WideIconSpacing))
        Column {
            Text(
                text = name,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(style.primaryTextColorRes),
                    fontSize = fittingFontSize(name, textWidth, statusSize * NameToStatusRatio),
                ),
            )
            Text(
                text = statusLabel,
                maxLines = 1,
                style = TextStyle(color = tint, fontSize = statusSize, fontWeight = FontWeight.Bold),
            )
            if (detailIfFits != null) {
                Text(
                    text = detailIfFits,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(style.secondaryTextColorRes),
                        fontSize = fittingFontSize(detailIfFits, textWidth, statusSize * DetailToStatusRatio),
                    ),
                )
            }
        }
    }
}

private fun widgetContainerModifier(padding: PaddingSize, style: LockWidgetStyle): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(ImageProvider(style.backgroundDrawableRes))
    .cornerRadius(R.dimen.lock_widget_corner_radius)
    .padding(horizontal = padding.horizontal, vertical = padding.vertical)

private fun DpSize.contentSize(padding: PaddingSize): DpSize = DpSize(width - padding.horizontal * 2, height - padding.vertical * 2)

private class PaddingSize(val horizontal: Dp, val vertical: Dp)

/**
 * Glance には文字の自動縮小が無いため、全角1文字を 1em 幅とみなして幅と高さの両方に収まる大きさを求める。
 * 半角が混ざると実際より狭く見積もるので、はみ出すことはない。
 * sp は端末の文字サイズ設定で拡大されるため、その倍率で割って dp に揃える。
 */
@Composable
private fun fittingFontSize(text: String, availableWidth: Dp, availableHeight: Dp): TextUnit {
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val byWidth = availableWidth.value / text.length.coerceAtLeast(1)
    val fitting = minOf(byWidth, availableHeight.value, MaxFontSize.value) / fontScale
    return fitting.coerceAtLeast(MinFontSize.value).sp
}

@Composable
private fun fittingFontSize(text: String, availableWidth: Dp, max: TextUnit): TextUnit {
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val byWidth = availableWidth.value / text.length.coerceAtLeast(1) / fontScale
    return byWidth.coerceIn(MinFontSize.value, max.value.coerceAtLeast(MinFontSize.value)).sp
}

private val WideSize = DpSize(110.dp, 40.dp)
private val WideWithDetailSize = DpSize(110.dp, 80.dp)
private val CompactPadding = PaddingSize(horizontal = 4.dp, vertical = 4.dp)
private val WidePadding = PaddingSize(horizontal = 10.dp, vertical = 4.dp)
private val WideIconSpacing = 8.dp
private val MinIconSize = 20.dp
private val MaxIconSize = 64.dp
private val MinFontSize = 10.sp
private val MaxFontSize = 40.sp
private const val CompactIconHeightRatio = 0.55f
private const val WideIconHeightRatio = 0.8f
private const val LineHeightRatio = 1.35f
private const val NameToStatusRatio = 0.55f
private const val DetailToStatusRatio = 0.5f

private fun LockWidgetIcon.tint(style: LockWidgetStyle): ColorProvider = ColorProvider(style.tintColorRes(this))
