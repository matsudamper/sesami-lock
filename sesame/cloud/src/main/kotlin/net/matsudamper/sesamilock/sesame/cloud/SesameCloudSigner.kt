package net.matsudamper.sesamilock.sesame.cloud

internal object SesameCloudSigner {
    /**
     * 公式 Web API のコード例と同じく、Unix 秒を UInt32 little-endian にした 4 バイトのうち [1..3] を
     * 秘密鍵で AES-CMAC し、小文字 16 進文字列にする。
     */
    fun sign(secretKey: ByteArray, epochSeconds: Long): String {
        val littleEndian = ByteArray(4) { index -> ((epochSeconds ushr (8 * index)) and 0xFF).toByte() }
        val message = littleEndian.copyOfRange(1, 4)
        return AesCmac.compute(secretKey, message).joinToString("") { "%02x".format(it) }
    }
}
