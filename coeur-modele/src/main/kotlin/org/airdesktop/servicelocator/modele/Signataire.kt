package org.airdesktop.servicelocator.modele

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
