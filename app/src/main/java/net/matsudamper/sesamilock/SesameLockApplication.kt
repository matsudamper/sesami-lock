package net.matsudamper.sesamilock

import android.app.Application
import android.content.Intent
import net.matsudamper.sesamilock.ui.overlay.LockOverlayActivity
import net.matsudamper.sesamilock.widget.LockWidgetController
import net.matsudamper.sesamilock.widget.LockWidgetHost

internal class SesameLockApplication : Application(), LockWidgetHost {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        registerActivityLifecycleCallbacks(graph.foregroundTracker)
        graph.start()
    }

    override val lockWidgetController: LockWidgetController get() = graph.widgetController

    override fun createLockOverlayIntent(lockKey: String): Intent = LockOverlayActivity.createIntent(this, lockKey)

    override fun createAppLaunchIntent(): Intent = Intent(this, MainActivity::class.java)
}
