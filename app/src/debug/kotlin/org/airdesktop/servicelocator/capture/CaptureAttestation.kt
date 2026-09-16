// Capture d'une chaîne d'attestation de clé Android — code de DÉBOGAGE, pour
// donner au serveur (`asl-keystore`) sa première chaîne réelle. Le geste est
// décrit dans le dépôt serveur, `docs/attestation/capture-keystore.md`.
//
// Une clé JETABLE, générée avec un défi d'attestation, dont on lit la chaîne
// de certificats : c'est une fonction du système (`setAttestationChallenge`),
// sans SDK de services — C19. Rien de ce qui sort n'est un secret : une chaîne
// d'attestation est publique par nature, le défi est un aléa, la clé n'a servi
// qu'à ça et elle est détruite ensuite.
package org.airdesktop.servicelocator.capture

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

private const val ETIQUETTE = "CAPTURE"
private const val ALIAS = "capture-attestation"

/** Génère une clé attestée, lit sa chaîne, imprime le bloc dans Logcat et le rend. */
fun capturerUneAttestation(contexte: Context): String {
    val lignes = mutableListOf("──── CAPTURE ATTESTATION ANDROID ────")
    try {
        val defi = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (magasin.containsAlias(ALIAS)) magasin.deleteEntry(ALIAS)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(defi)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
        val chaine = magasin.getCertificateChain(ALIAS)
        lignes += "DEFI=" + Base64.encodeToString(defi, Base64.NO_WRAP)
        chaine.forEachIndexed { i, certificat ->
            lignes += "CERT$i=" + Base64.encodeToString(certificat.encoded, Base64.NO_WRAP)
        }
        lignes += "PAQUET=" + contexte.packageName
        lignes += "SIGNATURE=" + empreinteDeSignature(contexte)
        lignes += "APPAREIL=${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · correctif ${Build.VERSION.SECURITY_PATCH}"
        magasin.deleteEntry(ALIAS)
    } catch (faute: Exception) {
        lignes += "FAUTE=${faute::class.java.simpleName}: ${faute.message}"
    }
    lignes += "──── FIN ────"
    lignes.forEach { Log.i(ETIQUETTE, it) }
    return lignes.joinToString("\n")
}

/** L'empreinte SHA-256 du certificat de signature de cette build, en hexadécimal. */
private fun empreinteDeSignature(contexte: Context): String {
    val infos = contexte.packageManager.getPackageInfo(contexte.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val signature = infos.signingInfo?.apkContentsSigners?.firstOrNull() ?: return "?"
    return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
}
