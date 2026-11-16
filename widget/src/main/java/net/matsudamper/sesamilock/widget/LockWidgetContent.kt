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
    public val color: Color,
    public val opacity: Opacity,
    @param:DrawableRes internal val backgroundDrawableRes: Int,
    @param:ColorRes public val backgroundColorRes: Int,
    @param:ColorRes public val outlineColorRes: Int,
    private val contentColors: LockWidgetContentColors,
) {
    WHITE_TRANSPARENT(
        color = Color.WHITE,
        opacity = Opacity.TRANSPARENT,
        backgroundDrawableRes = R.drawable.lock_widget_background_white_transparent,
        backgroundColorRes = android.R.color.transparent,
        outlineColorRes = R.color.lock_widget_white_outline,
        contentColors = LockWidgetContentColors.ON_DARK,
    ),
    WHITE_TRANSLUCENT(
        color = Color.WHITE,
        opacity = Opacity.TRANSLUCENT,
        backgroundDrawableRes = R.drawable.lock_widget_background_white_translucent,
        backgroundColorRes = R.color.lock_widget_white_translucent_background,
        outlineColorRes = android.R.color.transparent,
        contentColors = LockWidgetContentColors.ON_LIGHT,
    ),
    WHITE_OPAQUE(
        color = Color.WHITE,
        opacity = Opacity.OPAQUE,
        backgroundDrawableRes = R.drawable.lock_widget_background_white_opaque,
        backgroundColorRes = R.color.lock_widget_white_opaque_background,
        outlineColorRes = android.R.color.transparent,
        contentColors = LockWidgetContentColors.ON_LIGHT,
    ),
    BLACK_TRANSPARENT(
        color = Color.BLACK,
        opacity = Opacity.TRANSPARENT,
        backgroundDrawableRes = R.drawable.lock_widget_background_black_transparent,
        backgroundColorRes = android.R.color.transparent,
        outlineColorRes = R.color.lock_widget_black_outline,
        contentColors = LockWidgetContentColors.ON_LIGHT,
    ),
    BLACK_TRANSLUCENT(
        color = Color.BLACK,
        opacity = Opacity.TRANSLUCENT,
        backgroundDrawableRes = R.drawable.lock_widget_background_black_translucent,
        backgroundColorRes = R.color.lock_widget_black_translucent_background,
        outlineColorRes = android.R.color.transparent,
        contentColors = LockWidgetContentColors.ON_DARK,
    ),
    BLACK_OPAQUE(
        color = Color.BLACK,
        opacity = Opacity.OPAQUE,
        backgroundDrawableRes = R.drawable.lock_widget_background_black_opaque,
        backgroundColorRes = R.color.lock_widget_black_opaque_background,
        outlineColorRes = android.R.color.transparent,
        contentColors = LockWidgetContentColors.ON_DARK,
    ),
    ;

    @get:ColorRes
    public val primaryTextColorRes: Int get() = contentColors.primaryTextColorRes

    @get:ColorRes
    internal val secondaryTextColorRes: Int get() = contentColors.secondaryTextColorRes

    @ColorRes
    public fun tintColorRes(icon: LockWidgetIcon): Int = when (icon) {
        LockWidgetIcon.LOCKED -> contentColors.lockedColorRes
        LockWidgetIcon.UNLOCKED -> contentColors.unlockedColorRes
        LockWidgetIcon.MOVING, LockWidgetIcon.UNKNOWN -> contentColors.secondaryTextColorRes
    }

    public enum class Color {
        WHITE,
        BLACK,
    }

    public enum class Opacity {
        TRANSPARENT,
        TRANSLUCENT,
        OPAQUE,
    }

    public companion object {
        public fun of(color: Color, opacity: Opacity): LockWidgetStyle = entries.first { it.color == color && it.opacity == opacity }
    }
}

internal enum class LockWidgetContentColors(
    @param:ColorRes val primaryTextColorRes: Int,
    @param:ColorRes val secondaryTextColorRes: Int,
    @param:ColorRes val lockedColorRes: Int,
    @param:ColorRes val unlockedColorRes: Int,
) {
    ON_LIGHT(
        primaryTextColorRes = R.color.lock_widget_on_light_primary_text,
        secondaryTextColorRes = R.color.lock_widget_on_light_secondary_text,
        lockedColorRes = R.color.lock_widget_on_light_locked,
        unlockedColorRes = R.color.lock_widget_on_light_unlocked,
    ),
    ON_DARK(
        primaryTextColorRes = R.color.lock_widget_on_dark_primary_text,
        secondaryTextColorRes = R.color.lock_widget_on_dark_secondary_text,
        lockedColorRes = R.color.lock_widget_on_dark_locked,
        unlockedColorRes = R.color.lock_widget_on_dark_unlocked,
    ),
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
