package net.matsudamper.sesamilock.sesame.ble

import kotlin.math.abs
import net.matsudamper.sesamilock.sesame.core.model.MechanicalState

internal data class MechanicalReading(
    val isInLockRange: Boolean,
    val isInUnlockRange: Boolean,
    val isStopped: Boolean?,
    val isMoving: Boolean,
    val position: Int,
    val unlockPosition: Int?,
)

internal object MechanicalClassifier {
    /**
     * SDK の `Unlocked` は「施錠範囲ではない」を含むため、そのままは解錠確認済みにしない。
     * 解錠確認済みにするのは [UnlockVerificationPolicy.StoppedNearUnlockPosition] が有効で、
     * 停止していて解錠位置の許容範囲内にある場合だけ。
     */
    fun classify(reading: MechanicalReading, policy: UnlockVerificationPolicy): MechanicalState {
        val settled = !reading.isMoving && reading.isStopped != false
        if (!settled) return MechanicalState.NOT_LOCKED_OR_MOVING
        if (reading.isInLockRange) return MechanicalState.LOCKED
        return when (policy) {
            UnlockVerificationPolicy.Disabled -> MechanicalState.NOT_LOCKED_OR_MOVING

            is UnlockVerificationPolicy.StoppedNearUnlockPosition -> {
                val unlockPosition = reading.unlockPosition
                val verified = reading.isInUnlockRange &&
                    unlockPosition != null &&
                    abs(reading.position - unlockPosition) <= policy.maxPositionDeviation
                if (verified) MechanicalState.UNLOCKED_VERIFIED else MechanicalState.NOT_LOCKED_OR_MOVING
            }
        }
    }
}
