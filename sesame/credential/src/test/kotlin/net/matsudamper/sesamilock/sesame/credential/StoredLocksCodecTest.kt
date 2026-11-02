package net.matsudamper.sesamilock.sesame.credential

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

class StoredLocksCodecTest {
    private val uuid = "8C9A7BB0-1B3C-4F43-9A6E-6C3C2F2B5E01"

    @Test
    fun roundTripsBleAndCloudLocks() {
        val ble = RegisteredLock(
            id = requireNotNull(LockId.fromDeviceUuid(uuid)),
            displayName = "玄関",
            credential = LockCredential.Ble(uuid, SesameModel.SESAME_5, "0000", "0102030405060708090a0b0c0d0e0f10", "a1b2c3d4"),
        )
        val cloudUuid = "11111111-2222-3333-4444-555555555555"
        val cloud = RegisteredLock(
            id = requireNotNull(LockId.fromDeviceUuid(cloudUuid)),
            displayName = "勝手口",
            credential = LockCredential.Cloud(cloudUuid, SesameModel.SESAME_4, "api", "0102030405060708090a0b0c0d0e0f10"),
        )

        val decoded = requireNotNull(StoredLocksCodec.decode(StoredLocksCodec.encode(listOf(ble, cloud))))

        assertEquals(listOf(ble.id, cloud.id), decoded.map { it.id })
        assertEquals("a1b2c3d4", assertIs<LockCredential.Ble>(decoded[0].credential).publicKeyHex)
        assertEquals("api", assertIs<LockCredential.Cloud>(decoded[1].credential).apiKey)
    }

    @Test
    fun brokenDataIsNotPartiallyRestored() {
        assertNull(StoredLocksCodec.decode("{".toByteArray()))
        assertNull(StoredLocksCodec.decode("""{"version":1,"locks":[{"deviceUuid":"x","displayName":"a","deviceModel":"sesame_5","ble":null,"cloud":null}]}""".toByteArray()))
    }
}
