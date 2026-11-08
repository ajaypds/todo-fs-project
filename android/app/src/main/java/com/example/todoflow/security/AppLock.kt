package com.example.todoflow.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint / face / device PIN protection for opening the app. */
object AppLock {

    /**
     * The authenticator types this device can use (biometrics, falling back to the screen lock),
     * or null if no screen lock or biometrics are set up.
     */
    fun authenticators(context: Context): Int? {
        val manager = BiometricManager.from(context)
        val withCredential = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (manager.canAuthenticate(withCredential) == BiometricManager.BIOMETRIC_SUCCESS) return withCredential
        if (manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) return BIOMETRIC_WEAK
        return null
    }

    fun isAvailable(context: Context): Boolean = authenticators(context) != null

    /** Shows the system prompt. [onResult] receives true when the user authenticated. */
    fun prompt(activity: FragmentActivity, authenticators: Int, onResult: (Boolean) -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock TodoFlow")
            .setAllowedAuthenticators(authenticators)
            .apply {
                // A negative button is only allowed when the device credential isn't offered
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Cancel")
            }
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            }
        )
        biometricPrompt.authenticate(promptInfo)
    }
}
