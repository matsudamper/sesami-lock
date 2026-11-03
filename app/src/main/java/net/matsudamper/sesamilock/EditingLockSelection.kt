package net.matsudamper.sesamilock

import java.util.concurrent.atomic.AtomicReference
import net.matsudamper.sesamilock.sesame.core.model.LockId

internal class EditingLockSelection {
    private val selected = AtomicReference<LockId?>(null)

    fun select(lockId: LockId) {
        selected.set(lockId)
    }

    fun take(): LockId? = selected.getAndSet(null)
}
