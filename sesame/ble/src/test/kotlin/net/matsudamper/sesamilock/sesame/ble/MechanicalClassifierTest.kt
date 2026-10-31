package net.matsudamper.sesamilock.sesame.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState

class MechanicalClassifierTest {
    private val strict = UnlockVerificationPolicy.StoppedNearUnlockPosition(maxPositionDeviation = 30)

    private fun reading(
        isInLockRange: Boolean,
        isStopped: Boolean?,
        isMoving: Boolean = isStopped == false,
        position: Int = 0,
        unlockPosition: Int? = 0,
    ) = MechanicalReading(
        isInLockRange = isInLockRange,
        isInUnlockRange = !isInLockRange,
        isStopped = isStopped,
        isMoving = isMoving,
        position = position,
        unlockPosition = unlockPosition,
    )

    @Test
    fun stoppedInLockRangeIsLocked() {
        assertEquals(MechanicalState.LOCKED, MechanicalClassifier.classify(reading(isInLockRange = true, isStopped = true), UnlockVerificationPolicy.Disabled))
    }

    @Test
    fun movingInLockRangeIsNotLocked() {
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, MechanicalClassifier.classify(reading(isInLockRange = true, isStopped = false), UnlockVerificationPolicy.Disabled))
    }

    @Test
    fun outsideLockRangeIsNeverVerifiedUnlockWithoutStrictPolicy() {
        assertEquals(
            MechanicalState.NOT_LOCKED_OR_MOVING,
            MechanicalClassifier.classify(reading(isInLockRange = false, isStopped = true), UnlockVerificationPolicy.Disabled),
        )
    }

    @Test
    fun movingOutsideLockRangeIsNotVerifiedUnlockEvenWithStrictPolicy() {
        assertEquals(MechanicalState.NOT_LOCKED_OR_MOVING, MechanicalClassifier.classify(reading(isInLockRange = false, isStopped = false), strict))
    }

    @Test
    fun intermediatePositionIsNotVerifiedUnlock() {
        assertEquals(
            MechanicalState.NOT_LOCKED_OR_MOVING,
            MechanicalClassifier.classify(reading(isInLockRange = false, isStopped = true, position = 200, unlockPosition = 0), strict),
        )
    }

    @Test
    fun stoppedNearUnlockPositionIsVerifiedUnlockWithStrictPolicy() {
        assertEquals(
            MechanicalState.UNLOCKED_VERIFIED,
            MechanicalClassifier.classify(reading(isInLockRange = false, isStopped = true, position = 10, unlockPosition = 0), strict),
        )
    }

    @Test
    fun unknownUnlockPositionIsNotVerifiedUnlock() {
        assertEquals(
            MechanicalState.NOT_LOCKED_OR_MOVING,
            MechanicalClassifier.classify(reading(isInLockRange = false, isStopped = true, unlockPosition = null), strict),
        )
    }
}
