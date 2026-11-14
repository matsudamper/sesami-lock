package net.matsudamper.sesamilock.widget

import androidx.datastore.preferences.core.stringPreferencesKey

internal object LockWidgetKeys {
    val lockKey = stringPreferencesKey("lock_key")
    val name = stringPreferencesKey("name")
    val statusLabel = stringPreferencesKey("status_label")
    val statusIcon = stringPreferencesKey("status_icon")
    val detail = stringPreferencesKey("detail")
    val inProgressLabel = stringPreferencesKey("in_progress_label")
}
