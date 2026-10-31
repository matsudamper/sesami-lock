package net.matsudamper.sesamilock.sesame.ble

import kotlin.time.Duration

public data class SesameBleConfig(
    val historyTag: String,
    val acknowledgementTimeout: Duration,
    val unlockVerification: UnlockVerificationPolicy,
)

public sealed interface UnlockVerificationPolicy {
    public data object Disabled : UnlockVerificationPolicy

    public data class StoppedNearUnlockPosition(val maxPositionDeviation: Int) : UnlockVerificationPolicy
}
