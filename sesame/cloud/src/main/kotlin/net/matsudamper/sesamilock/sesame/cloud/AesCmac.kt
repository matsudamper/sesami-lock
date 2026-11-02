package net.matsudamper.sesamilock.sesame.cloud

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object AesCmac {
    private const val BLOCK_SIZE = 16
    private const val RB = 0x87

    /**
     * RFC 4493 の AES-CMAC。Android の標準プロバイダに CMAC が無い端末があるため AES-ECB から組み立てる。
     */
    fun compute(key: ByteArray, message: ByteArray): ByteArray {
        require(key.size == BLOCK_SIZE) { "AES-128 key is required" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        val k1 = shiftLeftWithRb(cipher.doFinal(ByteArray(BLOCK_SIZE)))
        val k2 = shiftLeftWithRb(k1)

        val blockCount = maxOf(1, (message.size + BLOCK_SIZE - 1) / BLOCK_SIZE)
        val lastBlockComplete = message.isNotEmpty() && message.size % BLOCK_SIZE == 0
        val lastStart = (blockCount - 1) * BLOCK_SIZE
        val lastBlock = if (lastBlockComplete) {
            xor(message.copyOfRange(lastStart, lastStart + BLOCK_SIZE), k1)
        } else {
            val padded = ByteArray(BLOCK_SIZE)
            val remaining = message.size - lastStart
            message.copyInto(padded, 0, lastStart, lastStart + remaining)
            padded[remaining] = 0x80.toByte()
            xor(padded, k2)
        }

        val state = (0 until blockCount - 1).fold(ByteArray(BLOCK_SIZE)) { acc, index ->
            val start = index * BLOCK_SIZE
            cipher.doFinal(xor(acc, message.copyOfRange(start, start + BLOCK_SIZE)))
        }
        return cipher.doFinal(xor(state, lastBlock))
    }

    private fun shiftLeftWithRb(input: ByteArray): ByteArray {
        val output = ByteArray(BLOCK_SIZE)
        for (i in 0 until BLOCK_SIZE) {
            val next = if (i + 1 < BLOCK_SIZE) (input[i + 1].toInt() and 0xFF) ushr 7 else 0
            output[i] = (((input[i].toInt() and 0xFF) shl 1) or next).toByte()
        }
        if ((input[0].toInt() and 0x80) != 0) {
            output[BLOCK_SIZE - 1] = (output[BLOCK_SIZE - 1].toInt() xor RB).toByte()
        }
        return output
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(BLOCK_SIZE) { (a[it].toInt() xor b[it].toInt()).toByte() }
}
