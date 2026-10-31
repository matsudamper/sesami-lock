package net.matsudamper.sesamilock.sesame.core.model

public interface LockClock {
    public fun elapsedRealtimeMillis(): Long

    public fun currentTimeMillis(): Long
}
