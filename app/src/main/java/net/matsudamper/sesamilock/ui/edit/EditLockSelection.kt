package net.matsudamper.sesamilock.ui.edit

import net.matsudamper.sesamilock.sesame.core.model.LockId

internal class EditLockSelection {
    @Volatile
    var lockId: LockId? = null
}
