package net.matsudamper.sesamilock.sesame.core.coordinator

import java.util.UUID
import net.matsudamper.sesamilock.sesame.core.model.RequestId

public fun interface RequestIdFactory {
    public fun create(): RequestId

    public companion object {
        public val RandomUuid: RequestIdFactory = RequestIdFactory { RequestId(UUID.randomUUID().toString()) }
    }
}
