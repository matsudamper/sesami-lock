package net.matsudamper.sesamilock.widget

import android.content.Intent
import androidx.annotation.DrawableRes

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

public enum class LockWidgetIcon(@param:DrawableRes internal val drawableRes: Int) {
    LOCKED(R.drawable.lock_widget_ic_lock),
    UNLOCKED(R.drawable.lock_widget_ic_lock_open_right),
    MOVING(R.drawable.lock_widget_ic_sync),
    UNKNOWN(R.drawable.lock_widget_ic_question_mark),
}

public interface LockWidgetHost {
    public val lockWidgetController: LockWidgetController

    public fun createLockOverlayIntent(lockKey: String): Intent

    public fun createAppLaunchIntent(): Intent
}
