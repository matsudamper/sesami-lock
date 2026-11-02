package net.matsudamper.sesamilock.sesame.core.model

public sealed interface LockCredential {
    public val route: LockRoute

    public class Ble(
        public val deviceUuid: String,
        public val model: SesameModel,
        public val keyIndexHex: String,
        public val secretKeyHex: String,
        public val publicKeyHex: String,
    ) : LockCredential {
        override val route: LockRoute = LockRoute.BLE

        override fun toString(): String = "LockCredential.Ble(model=$model, secret=<redacted>)"
    }

    public class Cloud(
        public val deviceUuid: String,
        public val model: SesameModel,
        public val apiKey: String,
        public val secretKeyHex: String,
    ) : LockCredential {
        override val route: LockRoute = LockRoute.CLOUD

        override fun toString(): String = "LockCredential.Cloud(model=$model, apiKey=<redacted>, secret=<redacted>)"
    }
}
