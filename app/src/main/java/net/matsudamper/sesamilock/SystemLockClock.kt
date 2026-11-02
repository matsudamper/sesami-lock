package net.matsudamper.sesamilock

import android.os.SystemClock
import net.matsudamper.sesamilock.sesame.core.model.LockClock

internal object SystemLockClock : LockClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
