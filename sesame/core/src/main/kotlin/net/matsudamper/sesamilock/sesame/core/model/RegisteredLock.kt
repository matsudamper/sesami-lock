package net.matsudamper.sesamilock.sesame.core.model

public class RegisteredLock(
    public val id: LockId,
    public val displayName: String,
    public val credential: LockCredential,
) {
    public val route: LockRoute get() = credential.route

    public val model: SesameModel
        get() = when (credential) {
            is LockCredential.Ble -> credential.model
            is LockCredential.Cloud -> credential.model
        }

    override fun toString(): String = "RegisteredLock(id=$id, route=$route, model=$model)"
}
