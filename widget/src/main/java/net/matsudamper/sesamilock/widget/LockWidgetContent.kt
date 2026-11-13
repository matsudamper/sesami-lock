package net.matsudamper.sesamilock.widget

import android.content.Intent

public data class LockWidgetContent(
    val name: String,
    val observedStatusIfKnown: ObservedStatus?,
    val inProgressLabel: String?,
) {
    public data class ObservedStatus(
        val label: String,
        val icon: LockWidgetIcon,
        val detail: String,
    )
}

public enum class LockWidgetIcon {
    LOCKED,
    UNLOCKED,
    MOVING,
    UNKNOWN,
}

public interface LockWidgetHost {
    public val lockWidgetController: LockWidgetController

    public fun createLockOverlayIntent(lockKey: String): Intent

    public fun createAppLaunchIntent(): Intent
}
