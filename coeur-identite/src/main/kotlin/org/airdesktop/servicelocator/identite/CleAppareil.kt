package org.airdesktop.servicelocator.identite

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.airdesktop.servicelocator.modele.NonConfirmeException
import org.airdesktop.servicelocator.modele.P256
import org.airdesktop.servicelocator.modele.Signataire
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume

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

        /** L'extension d'attestation de clé d'Android, sur la feuille d'une clé générée avec un défi. */
        private const val EXTENSION_ATTESTATION = "1.3.6.1.4.1.11129.2.1.17"

        /** La clé de cet appareil existe-t-elle déjà ? Sans la créer. */
        fun existe(): Boolean =
            KeyStore.getInstance(MAGASIN).apply { load(null) }.containsAlias(ALIAS)

        /**
         * Ouvre la clé de cet appareil, ou la crée si elle n'existe pas encore.
         * Créer ne demande pas la biométrie ; signer, si.
         *
         * `defiAttestation`, s'il est donné et que la clé est à créer, est posé
         * à la génération (`setAttestationChallenge`) : le Keystore rend alors
         * une chaîne de certificats qui atteste la clé — dans le TEE, sur un
         * appareil au démarrage vérifié, détenue par cette app — et c'est
         * [attestation] qui la rend, pour `POST /v1/comptes`. Une clé qui
         * existe déjà garde ce qu'elle a : le défi est ignoré.
         */
        fun ouOuvrir(defiAttestation: ByteArray? = null): CleAppareil {
            val magasin = KeyStore.getInstance(MAGASIN).apply { load(null) }
            val existante = magasin.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (existante != null) {
                return CleAppareil(existante.privateKey, P256.compresser(existante.certificate.publicKey as ECPublicKey))
            }
            val paire = try {
                generer(strongBox = true, defiAttestation)
            } catch (e: StrongBoxUnavailableException) {
                // Pas d'élément sécurisé dédié : le TEE, qui tient les mêmes garanties d'usage.
                generer(strongBox = false, defiAttestation)
            }
            return CleAppareil(paire.private, P256.compresser(paire.public as ECPublicKey))
        }

        private fun generer(strongBox: Boolean, defiAttestation: ByteArray?) = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, MAGASIN).run {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (defiAttestation != null) setAttestationChallenge(defiAttestation)
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

    /**
     * La chaîne d'attestation de la clé, pour le fil — `null` si la clé a été
     * générée sans défi (sa feuille ne porte pas l'extension d'attestation).
     * Feuille d'abord, chaque DER précédé de sa longueur sur deux octets
     * grand-boutiens ; la racine est envoyée aussi, l'annuaire la tient déjà.
     */
    val attestation: ByteArray?
        get() {
            val chaine = KeyStore.getInstance(MAGASIN).apply { load(null) }.getCertificateChain(ALIAS) ?: return null
            val feuille = chaine.firstOrNull() as? X509Certificate ?: return null
            if (feuille.getExtensionValue(EXTENSION_ATTESTATION) == null) return null
            val sortie = ByteArrayOutputStream()
            for (certificat in chaine) {
                val der = certificat.encoded
                if (der.size > 0xFFFF) return null
                sortie.write(der.size ushr 8)
                sortie.write(der.size and 0xFF)
                sortie.write(der)
            }
            return sortie.toByteArray()
        }

    /** Le signataire, lié à l'activité qui affichera le prompt. Pour un geste unique, dans cette activité-là. */
    fun avec(activite: FragmentActivity): Signataire = avec { activite }

    /**
     * Le signataire, qui demande **au moment de signer** quelle activité est
     * devant le porteur.
     *
     * Un transport tenu vit plus longtemps qu'une activité : une rotation
     * d'écran la détruit et la recrée, et une signature demandée après coup
     * doit s'afficher dans la nouvelle. Lier le signataire à une activité
     * précise l'aurait lié à une fenêtre morte. Sans activité au premier plan
     * (l'application est derrière), il n'y a personne à qui demander : la clé
     * ne signe pas, comme si le porteur avait refusé.
     */
    fun avec(activiteAuPremierPlan: () -> FragmentActivity?): Signataire = object : Signataire {
        override val clePublique = this@CleAppareil.clePublique
        override val attestation: ByteArray? get() = this@CleAppareil.attestation

        override suspend fun signer(message: ByteArray): ByteArray {
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privee) }
            // `BiometricPrompt` s'affiche depuis le fil principal, et de nulle
            // part ailleurs ; la signature, elle, se fait où l'on est — le
            // transport nous rappelle depuis un fil à lui.
            val debloquee = withContext(Dispatchers.Main) {
                val activite = activiteAuPremierPlan() ?: return@withContext null
                confirmer(activite, signature)
            } ?: throw NonConfirmeException()
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
