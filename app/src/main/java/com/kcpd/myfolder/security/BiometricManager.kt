package com.kcpd.myfolder.security

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages biometric authentication (fingerprint, face recognition).
 * Provides a secure way to unlock the vault using device biometrics.
 */
@Singleton
class BiometricManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Checks if biometric authentication is available on this device.
     *
     * @return BiometricAvailability status
     */
    fun checkBiometricAvailability(): BiometricAvailability {
        val biometricManager = AndroidBiometricManager.from(context)

        return when (biometricManager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS ->
                BiometricAvailability.Available

            AndroidBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NoHardware

            AndroidBiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.HardwareUnavailable

            AndroidBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NoneEnrolled

            AndroidBiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SecurityUpdateRequired

            AndroidBiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricAvailability.Unsupported

            AndroidBiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                BiometricAvailability.Unknown

            else -> BiometricAvailability.Unknown
        }
    }

    /**
     * Checks if biometric authentication can be used right now.
     */
    fun canUseBiometric(): Boolean {
        return checkBiometricAvailability() == BiometricAvailability.Available
    }

    /**
     * Authenticates the user using biometrics.
     *
     * @param activity The FragmentActivity to show the biometric prompt
     * @param title Title for the biometric prompt
     * @param subtitle Subtitle for the biometric prompt
     * @param description Description for the biometric prompt
     * @param onSuccess Callback when authentication succeeds
     * @param onError Callback when authentication fails (with error message)
     * @param onCancel Callback when user cancels authentication
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Vault",
        subtitle: String = "Use biometric to unlock",
        description: String? = null,
        onSuccess: () -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> {
                            onCancel()
                        }
                        else -> {
                            onError(errString.toString())
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Don't call onError here - this is just a retry opportunity
                    // The prompt stays open for the user to try again
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                description?.let { setDescription(it) }
            }
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Authenticates with a crypto object for stronger security.
     * This can be used to unwrap encryption keys after biometric verification.
     *
     * Note: This is an advanced feature for apps that want to tie encryption keys
     * to biometric authentication. Currently not implemented but provided as a
     * placeholder for future enhancement.
     */
    fun authenticateWithCrypto(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String = "Unlock Vault",
        subtitle: String = "Use biometric to unlock",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> {
                            onCancel()
                        }
                        else -> {
                            onError(errString.toString())
                        }
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}

/**
 * Represents the availability status of biometric authentication on the device.
 */
sealed class BiometricAvailability {
    /** Biometric authentication is available and can be used */
    object Available : BiometricAvailability()

    /** No biometric hardware detected on this device */
    object NoHardware : BiometricAvailability()

    /** Biometric hardware exists but is currently unavailable */
    object HardwareUnavailable : BiometricAvailability()

    /** User hasn't enrolled any biometrics (no fingerprints/face registered) */
    object NoneEnrolled : BiometricAvailability()

    /** Security update required before biometrics can be used */
    object SecurityUpdateRequired : BiometricAvailability()

    /** Biometric authentication is not supported */
    object Unsupported : BiometricAvailability()

    /** Status unknown */
    object Unknown : BiometricAvailability()

    /** Human-readable message for UI display */
    val message: String
        get() = when (this) {
            is Available -> "Biometric authentication available"
            is NoHardware -> "No biometric hardware detected"
            is HardwareUnavailable -> "Biometric hardware unavailable"
            is NoneEnrolled -> "No biometrics enrolled. Please set up fingerprint or face recognition in device settings."
            is SecurityUpdateRequired -> "Security update required"
            is Unsupported -> "Biometric authentication not supported"
            is Unknown -> "Biometric status unknown"
        }
}
