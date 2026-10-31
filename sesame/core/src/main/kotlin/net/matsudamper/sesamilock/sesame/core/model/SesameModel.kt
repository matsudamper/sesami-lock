package net.matsudamper.sesamilock.sesame.core.model

public enum class SesameProtocol(internal val publicKeyBytes: Int) {
    OS2(publicKeyBytes = 64),
    OS3(publicKeyBytes = 4),
}

public enum class SesameModel(
    public val deviceModel: String,
    public val productType: Int,
    public val displayName: String,
    public val protocol: SesameProtocol,
) {
    SESAME_3(deviceModel = "sesame_2", productType = 0, displayName = "SESAME 3", protocol = SesameProtocol.OS2),
    SESAME_4(deviceModel = "sesame_4", productType = 4, displayName = "SESAME 4", protocol = SesameProtocol.OS2),
    SESAME_5(deviceModel = "sesame_5", productType = 5, displayName = "SESAME 5", protocol = SesameProtocol.OS3),
    SESAME_5_PRO(deviceModel = "sesame_5_pro", productType = 7, displayName = "SESAME 5 Pro", protocol = SesameProtocol.OS3),
    SESAME_5_US(deviceModel = "sesame_5_us", productType = 16, displayName = "SESAME 5 US", protocol = SesameProtocol.OS3),
    SESAME_6(deviceModel = "sesame_6", productType = 20, displayName = "SESAME 6", protocol = SesameProtocol.OS3),
    SESAME_6_PRO(deviceModel = "sesame_6_pro", productType = 21, displayName = "SESAME 6 Pro", protocol = SesameProtocol.OS3),
    SESAME_MIWA(deviceModel = "sesame_miwa", productType = 29, displayName = "SESAME miwa", protocol = SesameProtocol.OS3),
    SESAME_6_PRO_SLIDING_DOOR(deviceModel = "sesame_6_pro_slidingdoor", productType = 32, displayName = "SESAME 6 Pro SlidingDoor", protocol = SesameProtocol.OS3),
    ;

    public companion object {
        public fun fromDeviceModel(deviceModel: String): SesameModel? = entries.firstOrNull { it.deviceModel == deviceModel }

        public fun fromProductType(productType: Int): SesameModel? = entries.firstOrNull { it.productType == productType }
    }
}
