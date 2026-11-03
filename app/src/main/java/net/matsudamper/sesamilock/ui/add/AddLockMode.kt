package net.matsudamper.sesamilock.ui.add

import net.matsudamper.sesamilock.sesame.core.model.LockId

internal sealed interface AddLockMode {
    data object New : AddLockMode

    data class Edit(val lockId: LockId?) : AddLockMode
}
