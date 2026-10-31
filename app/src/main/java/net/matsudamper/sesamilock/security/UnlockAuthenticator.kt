package net.matsudamper.sesamilock.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal interface UnlockAuthenticationListener {
    fun onAuthenticationSucceeded()

    fun onAuthenticationFailed(message: String)
}

internal object UnlockAuthenticator {
    private val allowedAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        listener: UnlockAuthenticationListener,
    ) {
        val availability = BiometricManager.from(activity).canAuthenticate(allowedAuthenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            listener.onAuthenticationFailed("端末認証を利用できません。画面ロックを設定してください")
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    listener.onAuthenticationSucceeded()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    listener.onAuthenticationFailed(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .setConfirmationRequired(true)
            .build()
        prompt.authenticate(info)
    }
}
