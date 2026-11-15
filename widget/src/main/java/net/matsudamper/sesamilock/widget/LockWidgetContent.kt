package net.matsudamper.sesamilock.widget

import android.content.Intent
import androidx.annotation.ColorRes
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

public enum class LockWidgetStyle(
    @param:DrawableRes internal val backgroundDrawableRes: Int,
    @param:ColorRes internal val primaryTextColorRes: Int,
    @param:ColorRes internal val secondaryTextColorRes: Int,
    @param:ColorRes private val lockedColorRes: Int,
    @param:ColorRes private val unlockedColorRes: Int,
) {
    WHITE_TRANSLUCENT(
        backgroundDrawableRes = R.drawable.lock_widget_background_white,
        primaryTextColorRes = R.color.lock_widget_white_primary_text,
        secondaryTextColorRes = R.color.lock_widget_white_secondary_text,
        lockedColorRes = R.color.lock_widget_white_locked,
        unlockedColorRes = R.color.lock_widget_white_unlocked,
    ),
    BLACK_TRANSLUCENT(
        backgroundDrawableRes = R.drawable.lock_widget_background_black,
        primaryTextColorRes = R.color.lock_widget_black_primary_text,
        secondaryTextColorRes = R.color.lock_widget_black_secondary_text,
        lockedColorRes = R.color.lock_widget_black_locked,
        unlockedColorRes = R.color.lock_widget_black_unlocked,
    ),
    TRANSPARENT(
        backgroundDrawableRes = R.drawable.lock_widget_background_transparent,
        primaryTextColorRes = R.color.lock_widget_black_primary_text,
        secondaryTextColorRes = R.color.lock_widget_black_secondary_text,
        lockedColorRes = R.color.lock_widget_black_locked,
        unlockedColorRes = R.color.lock_widget_black_unlocked,
    ),
    ;

    @ColorRes
    internal fun tintColorRes(icon: LockWidgetIcon): Int = when (icon) {
        LockWidgetIcon.LOCKED -> lockedColorRes
        LockWidgetIcon.UNLOCKED -> unlockedColorRes
        LockWidgetIcon.MOVING, LockWidgetIcon.UNKNOWN -> secondaryTextColorRes
    }
}

public data class LockWidgetSetting(
    val lockKeyIfAssigned: String?,
    val style: LockWidgetStyle,
)

public interface LockWidgetHost {
    public val lockWidgetController: LockWidgetController

    public fun createLockOverlayIntent(lockKey: String): Intent

    public fun createLockSelectionIntent(appWidgetId: Int): Intent
}
