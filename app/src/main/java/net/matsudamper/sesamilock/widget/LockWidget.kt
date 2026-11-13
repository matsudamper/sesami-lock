package net.matsudamper.sesamilock.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import net.matsudamper.sesamilock.MainActivity
import net.matsudamper.sesamilock.R
import net.matsudamper.sesamilock.ui.theme.darkStatusColors
import net.matsudamper.sesamilock.ui.theme.lightStatusColors

internal class LockWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val preferences = currentState<Preferences>()
            val lockIdValue = preferences[LockWidgetKeys.lockId]
            val inProgressLabel = preferences[LockWidgetKeys.inProgressLabel]
            GlanceTheme {
                if (lockIdValue == null) {
                    UnconfiguredContent(onClick = actionStartActivity(Intent(context, MainActivity::class.java)))
                } else {
                    LockContent(
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
                        onClick = actionStartActivity(LockOverlayActivity.createIntent(context, lockIdValue)),
                    )
                }
            }
        }
    }
}

internal class LockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockWidget()
}

@Composable
private fun UnconfiguredContent(onClick: Action) {
    Column(
        modifier = widgetContainerModifier().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "アプリの鍵のメニューから追加してください",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
        )
    }
}

@Composable
private fun LockContent(name: String, statusLabel: String, detail: String, icon: LockWidgetIcon, onClick: Action) {
    val tint = icon.tint()
    Row(
        modifier = widgetContainerModifier().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                when (icon) {
                    LockWidgetIcon.LOCKED -> R.drawable.ic_lock
                    LockWidgetIcon.UNLOCKED -> R.drawable.ic_lock_open_right
                    LockWidgetIcon.MOVING -> R.drawable.ic_sync
                    LockWidgetIcon.UNKNOWN -> R.drawable.ic_question_mark
                },
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column {
            Text(
                text = name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
            Text(
                text = statusLabel,
                maxLines = 1,
                style = TextStyle(color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = detail,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun widgetContainerModifier(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(GlanceTheme.colors.widgetBackground)
    .cornerRadius(16.dp)
    .padding(horizontal = 16.dp, vertical = 8.dp)

@Composable
private fun LockWidgetIcon.tint(): ColorProvider = when (this) {
    LockWidgetIcon.LOCKED -> ColorProvider(day = lightStatusColors.locked, night = darkStatusColors.locked)
    LockWidgetIcon.UNLOCKED -> ColorProvider(day = lightStatusColors.unlocked, night = darkStatusColors.unlocked)
    LockWidgetIcon.MOVING, LockWidgetIcon.UNKNOWN -> GlanceTheme.colors.onSurfaceVariant
}
