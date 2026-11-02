package net.matsudamper.sesamilock

import android.app.Application

internal class SesameLockApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        registerActivityLifecycleCallbacks(graph.foregroundTracker)
        graph.start()
    }
}
