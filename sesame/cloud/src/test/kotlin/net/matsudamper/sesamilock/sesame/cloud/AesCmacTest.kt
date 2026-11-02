package net.matsudamper.sesamilock.sesame.cloud

import kotlin.test.Test
import kotlin.test.assertEquals

class AesCmacTest {
    private val key = hex("2b7e151628aed2a6abf7158809cf4f3c")

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun rfc4493Example1EmptyMessage() {
        assertEquals("bb1d6929e95937287fa37d129b756746", AesCmac.compute(key, byteArrayOf()).toHex())
    }

    @Test
    fun rfc4493Example2SingleBlock() {
        val message = hex("6bc1bee22e409f96e93d7e117393172a")
        assertEquals("070a16b46b4d4144f79bdd9dd04a287c", AesCmac.compute(key, message).toHex())
    }

    @Test
    fun rfc4493Example3PartialBlock() {
        val message = hex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411")
        assertEquals("dfa66747de9ae63030ca32611497c827", AesCmac.compute(key, message).toHex())
    }

    @Test
    fun rfc4493Example4FourBlocks() {
        val message = hex(
            "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51" +
                "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        )
        assertEquals("51f0bebf7e3b9d92fc49741779363cfe", AesCmac.compute(key, message).toHex())
    }

    @Test
    fun signerUsesBytesOneToThreeOfLittleEndianTimestamp() {
        val epochSeconds = 0x12345678L
        val expected = AesCmac.compute(key, byteArrayOf(0x56, 0x34, 0x12)).toHex()

        assertEquals(expected, SesameCloudSigner.sign(key, epochSeconds))
    }
}
