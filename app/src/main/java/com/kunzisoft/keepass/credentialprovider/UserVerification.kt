package com.kunzisoft.keepass.credentialprovider

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.activities.dialogs.MainCredentialDialogFragment
import com.kunzisoft.keepass.credentialprovider.passkey.data.UserVerificationRequirement
import com.kunzisoft.keepass.database.ContextualDatabase
import com.kunzisoft.keepass.utils.getEnumExtra
import com.kunzisoft.keepass.utils.putEnumExtra
import com.kunzisoft.keepass.view.toastError

class UserVerification {

    companion object {

        private const val EXTRA_USER_VERIFICATION = "com.kunzisoft.keepass.extra.userVerification"
        private const val EXTRA_USER_VERIFIED_WITH_AUTH = "com.kunzisoft.keepass.extra.userVerifiedWithAuth"

        /**
         * Allowed authenticators for the User Verification
         */
        const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /**
         * Check if the device supports the biometric prompt for User Verification
         */
        fun Context.isAuthenticatorsAllowed(): Boolean {
            return BiometricManager.from(this)
                .canAuthenticate(ALLOWED_AUTHENTICATORS) == BIOMETRIC_SUCCESS
        }

        /**
         * Add the User Verification to the intent
         */
        fun Intent.addUserVerification(
            userVerification: UserVerificationRequirement,
            userVerifiedWithAuth: Boolean
        ) {
            putEnumExtra(EXTRA_USER_VERIFICATION, userVerification)
            putExtra(EXTRA_USER_VERIFIED_WITH_AUTH, userVerifiedWithAuth)
        }

        /**
         * Define if the User is verified with authentification from the intent
         */
        fun Intent.getUserVerifiedWithAuth(): Boolean {
            return getBooleanExtra(EXTRA_USER_VERIFIED_WITH_AUTH, true)
        }

        /**
         * Remove the User Verification from the intent
         */
        fun Intent.removeUserVerification() {
            removeExtra(EXTRA_USER_VERIFICATION)
        }

        /**
         * Remove the User verified with auth from the intent
         */
        fun Intent.removeUserVerifiedWithAuth() {
            removeExtra(EXTRA_USER_VERIFIED_WITH_AUTH)
        }

        /**
         * Get the User Verification from the intent
         */
        fun Intent.getUserVerificationCondition(): Boolean {
            return (getEnumExtra<UserVerificationRequirement>(EXTRA_USER_VERIFICATION)
                ?: UserVerificationRequirement.PREFERRED) == UserVerificationRequirement.REQUIRED
        }

        /**
         * Ask the user for verification
         * Ask for the biometric if defined on the device
         * Ask for the database credential otherwise
         */
        fun FragmentActivity.askUserVerification(
            database: ContextualDatabase,
            onVerificationSucceeded: () -> Unit,
            onVerificationFailed: () -> Unit
        ) {
            if (this.intent.getUserVerificationCondition()) {
                if (isAuthenticatorsAllowed()) {
                    BiometricPrompt(
                        this, ContextCompat.getMainExecutor(this),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                super.onAuthenticationError(errorCode, errString)
                                when (errorCode) {
                                    BiometricPrompt.ERROR_CANCELED,
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    BiometricPrompt.ERROR_USER_CANCELED -> {
                                        // No operation
                                        Log.i("UserVerification", "$errString")
                                    }
                                    else -> {
                                        toastError(SecurityException("Authentication error: $errString"))
                                    }
                                }
                                onVerificationFailed()
                            }
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                super.onAuthenticationSucceeded(result)
                                onVerificationSucceeded()
                            }
                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                toastError(SecurityException(getString(R.string.device_unlock_not_recognized)))
                                onVerificationFailed()
                            }
                        }).authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.user_verification_required))
                            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                            .setConfirmationRequired(false)
                            .build()
                    )
                } else {
                    MainCredentialDialogFragment.getInstance(database.fileUri)
                        .show(
                            supportFragmentManager,
                            MainCredentialDialogFragment.TAG_ASK_MAIN_CREDENTIAL
                        )
                }
            }
        }
    }
}