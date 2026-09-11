package org.airdesktop.servicelocator.identite

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Demande à l'appareil de confirmer l'identité de son porteur, ici et
 * maintenant.
 *
 * **Ce booléen n'est pas une preuve**, et il ne part nulle part. Dans le produit
 * fini, la confirmation est une condition d'usage de la clé du Keystore
 * (`setUserAuthenticationRequired(true)`) : c'est la signature qui la
 * déclenche, et le serveur ne voit que la signature. Tant que la clé n'est pas
 * écrite, ce geste tient sa place à l'écran — pour que l'utilisateur fasse le
 * même geste, au même moment.
 *
 * `BIOMETRIC_STRONG` seul, sans repli par code de verrouillage : l'énoncé du
 * produit demande une reconnaissance faciale ou une empreinte, pas un code.
 */
suspend fun IdentiteLocale.confirmer(activite: FragmentActivity, titre: String, sousTitre: String): Boolean =
    suspendCancellableCoroutine { suite ->
        val invite = BiometricPrompt(
            activite,
            ContextCompat.getMainExecutor(activite),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (suite.isActive) suite.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (suite.isActive) suite.resume(false)
                }
            },
        )
        invite.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(titre)
                .setSubtitle(sousTitre)
                .setNegativeButtonText("Annuler")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build(),
        )
        suite.invokeOnCancellation { invite.cancelAuthentication() }
    }
