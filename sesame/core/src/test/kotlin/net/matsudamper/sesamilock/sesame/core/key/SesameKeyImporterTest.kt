package net.matsudamper.sesamilock.sesame.core.key

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.matsudamper.sesamilock.sesame.core.model.SesameModel

class SesameKeyImporterTest {
    private val uuid = UUID.fromString("8c9a7bb0-1b3c-4f43-9a6e-6c3c2f2b5e01")
    private val secret = ByteArray(16) { (it + 1).toByte() }

    private fun legacyQr(productType: Int, publicKeyBytes: Int, name: String): String {
        val uuidBytes = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
        val payload = byteArrayOf(productType.toByte()) + secret + ByteArray(publicKeyBytes) { 0x11 } + byteArrayOf(0x00, 0x05) + uuidBytes
        val sk = URLEncoder.encode(Base64.getEncoder().encodeToString(payload), Charsets.UTF_8)
        return "ssm://UI?t=sk&sk=$sk&l=0&n=${URLEncoder.encode(name, Charsets.UTF_8)}"
    }

    @Test
    fun importsLegacyQrOfSesame5() {
        val result = assertIs<KeyImportResult.Success>(SesameKeyImporter.import(legacyQr(productType = 5, publicKeyBytes = 4, name = "玄関")))

        assertEquals(SesameModel.SESAME_5, result.credential.model)
        assertEquals(uuid.toString().uppercase(), result.credential.deviceUuid)
        assertEquals("0102030405060708090a0b0c0d0e0f10", result.credential.secretKeyHex)
        assertEquals("0005", result.credential.keyIndexHex)
        assertEquals("11111111", result.credential.publicKeyHex)
        assertEquals("玄関", result.suggestedName)
    }

    @Test
    fun importsLegacyQrOfSesame4() {
        val result = assertIs<KeyImportResult.Success>(SesameKeyImporter.import(legacyQr(productType = 4, publicKeyBytes = 64, name = "a")))

        assertEquals(SesameModel.SESAME_4, result.credential.model)
        assertEquals(128, result.credential.publicKeyHex.length)
    }

    @Test
    fun rejectsQrWhoseLengthDoesNotMatchModel() {
        val result = SesameKeyImporter.import(legacyQr(productType = 5, publicKeyBytes = 64, name = "a"))

        assertEquals(KeyImportResult.Failure(KeyImportError.ENCRYPTED_TOKEN_QR), result)
    }

    @Test
    fun rejectsNonLockProducts() {
        val result = SesameKeyImporter.import(legacyQr(productType = 2, publicKeyBytes = 64, name = "bot"))

        assertEquals(KeyImportResult.Failure(KeyImportError.UNSUPPORTED_MODEL), result)
    }

    @Test
    fun importsOfficialJsonExport() {
        val json = """
            {"deviceUUID":"${uuid.toString().uppercase()}","deviceModel":"sesame_5_pro","keyIndex":"0000",
             "secretKey":"0102030405060708090a0b0c0d0e0f10","sesame2PublicKey":"a1b2c3d4"}
        """.trimIndent()

        val result = assertIs<KeyImportResult.Success>(SesameKeyImporter.import(json))

        assertEquals(SesameModel.SESAME_5_PRO, result.credential.model)
    }

    @Test
    fun rejectsJsonWithUnsupportedModel() {
        val json = """{"deviceUUID":"$uuid","deviceModel":"bike_2","keyIndex":"0000","secretKey":"0102030405060708090a0b0c0d0e0f10","sesame2PublicKey":"a1b2c3d4"}"""

        assertEquals(KeyImportResult.Failure(KeyImportError.UNSUPPORTED_MODEL), SesameKeyImporter.import(json))
    }

    @Test
    fun rejectsGuestKeyThatNeedsServerSignature() {
        val json = """{"deviceUUID":"$uuid","deviceModel":"sesame_5","keyIndex":"0000","secretKey":"00000000000000000000000000000000","sesame2PublicKey":"a1b2c3d4"}"""

        assertEquals(KeyImportResult.Failure(KeyImportError.SERVER_SIGNED_GUEST_KEY), SesameKeyImporter.import(json))
    }

    @Test
    fun rejectsUnknownText() {
        assertEquals(KeyImportResult.Failure(KeyImportError.UNRECOGNIZED_FORMAT), SesameKeyImporter.import("hello"))
        assertEquals(KeyImportResult.Failure(KeyImportError.EMPTY), SesameKeyImporter.import("  "))
    }

    @Test
    fun credentialToStringDoesNotLeakSecret() {
        val result = assertIs<KeyImportResult.Success>(SesameKeyImporter.import(legacyQr(productType = 5, publicKeyBytes = 4, name = "a")))

        assertEquals(false, result.credential.toString().contains(result.credential.secretKeyHex))
    }
}
