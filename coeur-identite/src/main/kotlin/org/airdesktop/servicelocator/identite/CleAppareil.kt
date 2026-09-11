package org.airdesktop.servicelocator.identite

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Messages
import org.airdesktop.servicelocator.modele.P256
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume

/**
 * Ce qu'une clé d'appareil sait faire — et la seule chose que les écrans en
 * voient. Le Keystore matériel la met en œuvre sur un appareil ; une clé
 * logicielle la met en œuvre dans un essai.
 */
interface Signataire {
    /** La clé publique, SEC1 compressée : `02` ou `03` ‖ x — 33 octets. */
    val clePublique: ByteArray

    /**
     * Signe en ECDSA P-256 sur SHA-256, et rend `r ‖ s` — 64 octets. C'est ici
     * que la biométrie est demandée, et c'est pourquoi c'est `suspend`.
     */
    suspend fun signer(message: ByteArray): ByteArray

    /** La preuve de possession de `POST /v1/comptes` : la clé signe le message qui la contient. */
    suspend fun prouverLaPossession(defi: ByteArray, liaison: ByteArray): ByteArray =
        signer(Messages.dePossession(clePublique, defi, liaison))

    /** La signature d'authentification d'un appareil enrôlé. */
    suspend fun authentifier(appareil: Identifiant, defi: ByteArray, liaison: ByteArray): ByteArray =
        signer(Messages.aSigner(appareil, defi, liaison))
}

/** L'appareil n'a pas confirmé l'identité de son porteur : la clé n'a pas signé. */
class NonConfirmeException : Exception("identité non confirmée")

/**
 * La clé P-256 de cet appareil, dans le Keystore matériel, sous contrôle
 * biométrique.
 *
 * # La biométrie est une condition d'usage de la clé, appliquée par le matériel
 *
 * `setUserAuthenticationRequired(true)`, biométrie forte, **à chaque usage** :
 * le système refuse de signer tant que le porteur n'a pas été reconnu, et la
 * clé est invalidée si le jeu d'empreintes ou de visages change. Ce n'est pas
 * un booléen que le code transporte — c'est le Keystore qui refuse. Le serveur
 * ne verra jamais que la signature.
 *
 * **La partie privée ne sort jamais.** Elle est générée dans le TEE — dans
 * StrongBox quand l'appareil en a un —, et `allowBackup="false"` dans le
 * manifeste fait qu'aucune sauvegarde ne prétend la restaurer ailleurs.
 *
 * # P-256, et rien d'autre
 *
 * StrongBox ne fait que cette courbe. Les machines signent en Ed25519 ; les
 * appareils en ECDSA P-256, et c'est la clé rangée dans l'annuaire qui dit,
 * par sa forme, comment vérifier (`asl_cle::CleAppareil`).
 *
 * # Le geste
 *
 * Signer passe par `BiometricPrompt` et un `CryptoObject` : c'est le prompt
 * qui débloque l'objet `Signature` déjà initialisé sur la clé, et lui seul.
 * Une `FragmentActivity` est donc nécessaire au moment de signer — celle qui
 * affiche l'écran.
 */
class CleAppareil private constructor(private val privee: PrivateKey, val clePublique: ByteArray) {

    companion object {
        private const val ALIAS = "org.airdesktop.servicelocator.cle-appareil"
        private const val MAGASIN = "AndroidKeyStore"

        /** Ouvre la clé de cet appareil, ou la crée si elle n'existe pas encore. Créer ne demande pas la biométrie ; signer, si. */
        fun ouOuvrir(): CleAppareil {
            val magasin = KeyStore.getInstance(MAGASIN).apply { load(null) }
            val existante = magasin.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (existante != null) {
                return CleAppareil(existante.privateKey, P256.compresser(existante.certificate.publicKey as ECPublicKey))
            }
            val paire = try {
                generer(strongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                // Pas d'élément sécurisé dédié : le TEE, qui tient les mêmes garanties d'usage.
                generer(strongBox = false)
            }
            return CleAppareil(paire.private, P256.compresser(paire.public as ECPublicKey))
        }

        private fun generer(strongBox: Boolean) = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, MAGASIN).run {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    // Une confirmation PAR SIGNATURE, biométrie forte seule. Avant
                    // Android 11, c'est la durée -1 qui dit la même chose.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                    if (strongBox) setIsStrongBoxBacked(true)
                }
                .build()
            initialize(spec)
            generateKeyPair()
        }

        /** Efface la clé de cet appareil. Sans retour : l'annuaire refusera la prochaine authentification, et il faudra ré-enrôler. */
        fun effacer() {
            KeyStore.getInstance(MAGASIN).apply { load(null) }.deleteEntry(ALIAS)
        }
    }

    /** Le signataire, lié à l'activité qui affichera le prompt. */
    fun avec(activite: FragmentActivity): Signataire = object : Signataire {
        override val clePublique = this@CleAppareil.clePublique

        override suspend fun signer(message: ByteArray): ByteArray {
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privee) }
            val debloquee = confirmer(activite, signature) ?: throw NonConfirmeException()
            debloquee.update(message)
            return P256.deplierDER(debloquee.sign())
        }
    }

    private suspend fun confirmer(activite: FragmentActivity, signature: Signature): Signature? =
        suspendCancellableCoroutine { suite ->
            val invite = BiometricPrompt(
                activite,
                ContextCompat.getMainExecutor(activite),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (suite.isActive) suite.resume(result.cryptoObject?.signature)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (suite.isActive) suite.resume(null)
                    }
                },
            )
            invite.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Signer avec la clé de cet appareil")
                    .setSubtitle("Votre identité est confirmée ici, et n'en sort pas.")
                    .setNegativeButtonText("Annuler")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build(),
                BiometricPrompt.CryptoObject(signature),
            )
            suite.invokeOnCancellation { invite.cancelAuthentication() }
        }
}
