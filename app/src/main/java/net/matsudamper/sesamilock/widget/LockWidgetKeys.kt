package net.matsudamper.sesamilock.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute

internal object LockWidgetKeys {
    val lockId = stringPreferencesKey("lock_id")
    val name = stringPreferencesKey("name")
    val statusLabel = stringPreferencesKey("status_label")
    val statusIcon = stringPreferencesKey("status_icon")
    val detail = stringPreferencesKey("detail")
}

internal enum class LockWidgetIcon {
    LOCKED,
    UNLOCKED,
    MOVING,
    UNKNOWN,
}

internal fun parseLockId(value: String): LockId? {
    val route = LockRoute.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return null
    return LockId.of(value.substringAfter(':'), route)
}
