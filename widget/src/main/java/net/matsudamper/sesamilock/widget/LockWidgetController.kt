package net.matsudamper.sesamilock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class LockWidgetController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var latestContents: Map<String, LockWidgetContent>? = null
    private val writeMutex = Mutex()

    public val pinSupported: Boolean get() = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    public fun requestPin(lockKey: String) {
        val callback = PendingIntent.getBroadcast(
            context,
            lockKey.hashCode(),
            LockWidgetPinnedReceiver.createIntent(context, lockKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, LockWidgetReceiver::class.java),
            Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, createPinPreview(latestContents?.get(lockKey))) },
            callback,
        )
    }

    public suspend fun updateAll(contents: Map<String, LockWidgetContent>) {
        latestContents = contents
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(LockWidget::class.java)
        writeMutex.withLock {
            glanceIds.forEach { glanceId -> update(glanceId, contents) }
        }
    }

    public fun isLockWidget(appWidgetId: Int): Boolean = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider == ComponentName(context, LockWidgetReceiver::class.java)

    public suspend fun currentSetting(appWidgetId: Int): LockWidgetSetting {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return LockWidgetSetting(
            lockKeyIfAssigned = preferences[LockWidgetKeys.lockKey],
            style = preferences.lockWidgetStyle(),
        )
    }

    public fun applyStyle(appWidgetId: Int, style: LockWidgetStyle): Job = scope.launch {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        writeMutex.withLock {
            updateAppWidgetState(context, glanceId) { preferences ->
                preferences[LockWidgetKeys.style] = style.name
            }
            LockWidget().update(context, glanceId)
        }
    }

    public fun assign(appWidgetId: Int, lockKey: String): Job = scope.launch {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        writeMutex.withLock {
            updateAppWidgetState(context, glanceId) { preferences ->
                val style = preferences.lockWidgetStyle()
                preferences.clear()
                preferences[LockWidgetKeys.lockKey] = lockKey
                preferences[LockWidgetKeys.style] = style.name
                latestContents?.get(lockKey)?.let { preferences.write(it) }
            }
            LockWidget().update(context, glanceId)
        }
    }

    private fun createPinPreview(content: LockWidgetContent?): RemoteViews {
        val preview = RemoteViews(context.packageName, R.layout.lock_widget_preview)
        if (content == null) return preview
        val icon = if (content.inProgressLabel != null) {
            LockWidgetIcon.MOVING
        } else {
            content.observedStatusIfKnown?.icon ?: LockWidgetIcon.UNKNOWN
        }
        val tint = context.getColor(PinPreviewStyle.tintColorRes(icon))
        preview.setTextViewText(R.id.lock_widget_preview_name, content.name)
        preview.setTextViewText(
            R.id.lock_widget_preview_status,
            content.inProgressLabel ?: content.observedStatusIfKnown?.label ?: "状態不明",
        )
        preview.setTextColor(R.id.lock_widget_preview_status, tint)
        preview.setImageViewResource(R.id.lock_widget_preview_icon, icon.drawableRes)
        preview.setInt(R.id.lock_widget_preview_icon, "setColorFilter", tint)
        return preview
    }

    private suspend fun update(glanceId: GlanceId, contents: Map<String, LockWidgetContent>) {
        updateAppWidgetState(context, glanceId) { preferences ->
            val lockKey = preferences[LockWidgetKeys.lockKey] ?: return@updateAppWidgetState
            val content = contents[lockKey]
            if (content == null) {
                preferences[LockWidgetKeys.statusLabel] = "鍵が削除されました"
                preferences[LockWidgetKeys.statusIcon] = LockWidgetIcon.UNKNOWN.name
                preferences.remove(LockWidgetKeys.detail)
                preferences.remove(LockWidgetKeys.inProgressLabel)
            } else {
                preferences.write(content)
            }
        }
        LockWidget().update(context, glanceId)
    }

    private fun MutablePreferences.write(content: LockWidgetContent) {
        this[LockWidgetKeys.name] = content.name
        if (content.inProgressLabel == null) {
            remove(LockWidgetKeys.inProgressLabel)
        } else {
            this[LockWidgetKeys.inProgressLabel] = content.inProgressLabel
        }
        val status = content.observedStatusIfKnown ?: return
        this[LockWidgetKeys.statusLabel] = status.label
        this[LockWidgetKeys.statusIcon] = status.icon.name
        this[LockWidgetKeys.detail] = status.detail
    }
}

private val PinPreviewStyle = LockWidgetStyle.WHITE_TRANSLUCENT

internal class LockWidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val lockKey = intent.getStringExtra(EXTRA_LOCK_KEY)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || lockKey == null) return
        val host = context.applicationContext as LockWidgetHost
        val pendingResult = goAsync()
        host.lockWidgetController.assign(appWidgetId, lockKey).invokeOnCompletion { pendingResult.finish() }
    }

    companion object {
        private const val EXTRA_LOCK_KEY = "lock_key"

        fun createIntent(context: Context, lockKey: String): Intent = Intent(context, LockWidgetPinnedReceiver::class.java)
            .putExtra(EXTRA_LOCK_KEY, lockKey)
    }
}
