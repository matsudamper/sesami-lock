package net.matsudamper.sesamilock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import net.matsudamper.sesamilock.sesame.core.coordinator.ForegroundState

internal class ActivityForegroundTracker(
    private val onVisibilityChanged: (visible: Boolean) -> Unit,
) : ForegroundState, Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0

    override fun isInForeground(): Boolean = synchronized(this) { startedActivityCount > 0 }

    override fun onActivityStarted(activity: Activity) {
        val becameVisible = synchronized(this) {
            startedActivityCount += 1
            startedActivityCount == 1
        }
        if (becameVisible) onVisibilityChanged(true)
    }

    override fun onActivityStopped(activity: Activity) {
        val becameInvisible = synchronized(this) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            startedActivityCount == 0
        }
        if (becameInvisible) onVisibilityChanged(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?): Unit = Unit

    override fun onActivityResumed(activity: Activity): Unit = Unit

    override fun onActivityPaused(activity: Activity): Unit = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle): Unit = Unit

    override fun onActivityDestroyed(activity: Activity): Unit = Unit
}
