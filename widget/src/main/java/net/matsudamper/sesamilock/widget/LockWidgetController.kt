package net.matsudamper.sesamilock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
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
            null,
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

    internal fun assign(appWidgetId: Int, lockKey: String): Job = scope.launch {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        writeMutex.withLock {
            updateAppWidgetState(context, glanceId) { preferences ->
                preferences.clear()
                preferences[LockWidgetKeys.lockKey] = lockKey
                latestContents?.get(lockKey)?.let { preferences.write(it) }
            }
            LockWidget().update(context, glanceId)
        }
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
