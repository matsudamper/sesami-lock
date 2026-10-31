package net.matsudamper.sesamilock.sesame.core.coordinator

import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute

public data class LockTarget(val lockId: LockId, val route: LockRoute)
