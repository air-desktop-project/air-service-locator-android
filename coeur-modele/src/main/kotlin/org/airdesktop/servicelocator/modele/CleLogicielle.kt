package org.airdesktop.servicelocator.modele

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Une clé P-256 logicielle, pour les essais et les bancs — jamais sur un
 * appareil : elle n'est protégée par rien. Elle vit ici, en Kotlin pur, pour
 * que les essais JVM signent sans Keystore.
 */
class CleLogicielle : Signataire {
    private val privee: PrivateKey
    /** SEC1 compressé, 33 octets. */
    override val clePublique: ByteArray

    init {
        val paire = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        privee = paire.private
        clePublique = P256.compresser(paire.public as ECPublicKey)
    }

    /** ECDSA P-256 sur SHA-256, `r ‖ s`. Synchrone : aucune biométrie ici. */
    fun signerSync(message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privee)
        update(message)
        P256.deplierDER(sign())
    }

    override suspend fun signer(message: ByteArray): ByteArray = signerSync(message)
}
