package net.matsudamper.sesamilock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import net.matsudamper.sesamilock.SesameLockApplication
import net.matsudamper.sesamilock.sesame.core.model.LockId

internal class LockWidgetPinner(private val context: Context) {
    val supported: Boolean get() = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPin(lockId: LockId) {
        val callback = PendingIntent.getBroadcast(
            context,
            lockId.value.hashCode(),
            LockWidgetPinnedReceiver.createIntent(context, lockId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, LockWidgetReceiver::class.java),
            null,
            callback,
        )
    }
}

internal class LockWidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val lockId = intent.getStringExtra(EXTRA_LOCK_ID)?.let(::parseLockId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || lockId == null) return
        val graph = (context.applicationContext as SesameLockApplication).graph
        val pendingResult = goAsync()
        graph.assignWidget(appWidgetId, lockId).invokeOnCompletion { pendingResult.finish() }
    }

    companion object {
        private const val EXTRA_LOCK_ID = "lock_id"

        fun createIntent(context: Context, lockId: LockId): Intent = Intent(context, LockWidgetPinnedReceiver::class.java)
            .putExtra(EXTRA_LOCK_ID, lockId.value)
    }
}
