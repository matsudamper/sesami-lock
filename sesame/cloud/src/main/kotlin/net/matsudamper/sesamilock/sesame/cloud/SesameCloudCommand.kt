package net.matsudamper.sesamilock.sesame.cloud

import net.matsudamper.sesamilock.sesame.core.model.LockIntent

internal enum class SesameCloudCommand(val code: Int) {
    LOCK(82),
    UNLOCK(83),
    ;

    companion object {
        fun from(intent: LockIntent): SesameCloudCommand = when (intent) {
            LockIntent.LOCK -> LOCK
            LockIntent.UNLOCK -> UNLOCK
        }
    }
}
