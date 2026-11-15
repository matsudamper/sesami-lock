package net.matsudamper.sesamilock.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(CompactSize, WideSize))

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
    val compact = LocalSize.current.width < WideSize.width
    Column(
        modifier = widgetContainerModifier(compact, style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (compact) "鍵を選択" else "タップして鍵を選択してください",
            style = TextStyle(color = ColorProvider(style.primaryTextColorRes), fontSize = if (compact) 11.sp else 13.sp),
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
    if (LocalSize.current.width < WideSize.width) {
        CompactLockContent(style = style, statusLabel = statusLabel, icon = icon, onClick = onClick)
    } else {
        WideLockContent(style = style, name = name, statusLabel = statusLabel, detail = detail, icon = icon, onClick = onClick)
    }
}

@Composable
private fun CompactLockContent(style: LockWidgetStyle, statusLabel: String, icon: LockWidgetIcon, onClick: Action) {
    val tint = icon.tint(style)
    Column(
        modifier = widgetContainerModifier(compact = true, style = style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(icon.drawableRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(28.dp),
        )
        Text(
            text = statusLabel,
            maxLines = 1,
            style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun WideLockContent(style: LockWidgetStyle, name: String, statusLabel: String, detail: String, icon: LockWidgetIcon, onClick: Action) {
    val tint = icon.tint(style)
    Row(
        modifier = widgetContainerModifier(compact = false, style = style).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon.drawableRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column {
            Text(
                text = name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(style.primaryTextColorRes), fontSize = 13.sp),
            )
            Text(
                text = statusLabel,
                maxLines = 1,
                style = TextStyle(color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = detail,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(style.secondaryTextColorRes), fontSize = 11.sp),
            )
        }
    }
}

private fun widgetContainerModifier(compact: Boolean, style: LockWidgetStyle): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(ImageProvider(style.backgroundDrawableRes))
    .cornerRadius(16.dp)
    .padding(horizontal = if (compact) 4.dp else 16.dp, vertical = if (compact) 4.dp else 8.dp)

private val CompactSize = DpSize(40.dp, 40.dp)
private val WideSize = DpSize(110.dp, 40.dp)

private fun LockWidgetIcon.tint(style: LockWidgetStyle): ColorProvider = ColorProvider(style.tintColorRes(this))
