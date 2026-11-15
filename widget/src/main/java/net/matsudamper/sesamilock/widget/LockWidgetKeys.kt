package net.matsudamper.sesamilock.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

internal object LockWidgetKeys {
    val lockKey = stringPreferencesKey("lock_key")
    val name = stringPreferencesKey("name")
    val statusLabel = stringPreferencesKey("status_label")
    val statusIcon = stringPreferencesKey("status_icon")
    val detail = stringPreferencesKey("detail")
    val inProgressLabel = stringPreferencesKey("in_progress_label")
    val style = stringPreferencesKey("style")
}

internal fun Preferences.lockWidgetStyle(): LockWidgetStyle {
    val stored = this[LockWidgetKeys.style]
    return LockWidgetStyle.entries.firstOrNull { it.name == stored } ?: LockWidgetStyle.WHITE_TRANSLUCENT
}
