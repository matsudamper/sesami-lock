package net.matsudamper.sesamilock.sesame.core.coordinator

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public data class LockSafetyConfig(
    val unlockSendDeadlineAfterTap: Duration,
    val lockSendDeadlineAfterTap: Duration,
    val verificationTimeout: Duration,
    val verificationSettleDuration: Duration,
    val stateRefreshInterval: Duration,
) {
    public companion object {
        public val Default: LockSafetyConfig = LockSafetyConfig(
            unlockSendDeadlineAfterTap = 10.seconds,
            lockSendDeadlineAfterTap = 15.seconds,
            verificationTimeout = 12.seconds,
            verificationSettleDuration = 1500.milliseconds,
            stateRefreshInterval = 2.seconds,
        )
    }
}
