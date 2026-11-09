package net.matsudamper.sesamilock.sesame.core.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.matsudamper.sesamilock.sesame.core.model.LockCredential
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

class LockTransferCodecTest {
    private val uuid = "8C9A7BB0-1B3C-4F43-9A6E-6C3C2F2B5E01"
    private val secret = "0102030405060708090a0b0c0d0e0f10"

    @Test
    fun roundTripsBleLock() {
        val credential = LockCredential.Ble(uuid, SesameModel.SESAME_5, "0005", secret, "11111111")
        val lock = RegisteredLock(LockId.of(uuid, LockRoute.BLE)!!, "玄関", credential)

        val result = assertIs<LockTransferResult.Success>(LockTransferCodec.decode(LockTransferCodec.encode(lock)))

        val decoded = assertIs<LockCredential.Ble>(result.credential)
        assertEquals("玄関", result.displayName)
        assertEquals(uuid, decoded.deviceUuid)
        assertEquals(SesameModel.SESAME_5, decoded.model)
        assertEquals("0005", decoded.keyIndexHex)
        assertEquals(secret, decoded.secretKeyHex)
        assertEquals("11111111", decoded.publicKeyHex)
    }

    @Test
    fun roundTripsCloudLock() {
        val credential = LockCredential.Cloud(uuid, SesameModel.SESAME_6, "api-key", secret)
        val lock = RegisteredLock(LockId.of(uuid, LockRoute.CLOUD)!!, "勝手口", credential)

        val result = assertIs<LockTransferResult.Success>(LockTransferCodec.decode(LockTransferCodec.encode(lock)))

        val decoded = assertIs<LockCredential.Cloud>(result.credential)
        assertEquals("勝手口", result.displayName)
        assertEquals("api-key", decoded.apiKey)
        assertEquals(secret, decoded.secretKeyHex)
        assertEquals(SesameModel.SESAME_6, decoded.model)
    }

    @Test
    fun keepsSeparatorsInDisplayNameAndApiKey() {
        val credential = LockCredential.Cloud(uuid, SesameModel.SESAME_6, "a|b\\c", secret)
        val lock = RegisteredLock(LockId.of(uuid, LockRoute.CLOUD)!!, "玄関|1階\\", credential)

        val result = assertIs<LockTransferResult.Success>(LockTransferCodec.decode(LockTransferCodec.encode(lock)))

        assertEquals("玄関|1階\\", result.displayName)
        assertEquals("a|b\\c", assertIs<LockCredential.Cloud>(result.credential).apiKey)
    }

    @Test
    fun doesNotInflateJapaneseDisplayName() {
        val credential = LockCredential.Cloud(uuid, SesameModel.SESAME_6, "k", secret)
        val lock = RegisteredLock(LockId.of(uuid, LockRoute.CLOUD)!!, "玄関", credential)

        assertTrue(LockTransferCodec.encode(lock).endsWith("|玄関"))
    }

    @Test
    fun rejectsOfficialKeyJson() {
        val official = """{"deviceUUID":"$uuid","deviceModel":"sesame_5","keyIndex":"0005","secretKey":"$secret","sesame2PublicKey":"11111111"}"""

        val result = assertIs<LockTransferResult.Failure>(LockTransferCodec.decode(official))

        assertEquals(LockTransferError.NOT_TRANSFER_DATA, result.error)
    }

    @Test
    fun rejectsUnknownVersion() {
        val data = "SL2|BLE|$uuid|5|0005|$secret|11111111|a"

        assertEquals(LockTransferError.UNSUPPORTED_VERSION, assertIs<LockTransferResult.Failure>(LockTransferCodec.decode(data)).error)
    }

    @Test
    fun rejectsBrokenSecretKey() {
        val data = "SL1|CLOUD|$uuid|5|zz|k|a"

        assertEquals(LockTransferError.INVALID_CONTENT, assertIs<LockTransferResult.Failure>(LockTransferCodec.decode(data)).error)
    }
}
