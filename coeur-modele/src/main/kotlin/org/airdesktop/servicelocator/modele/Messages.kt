package org.airdesktop.servicelocator.modele

/**
 * Ce qu'un appareil signe, octet pour octet, tel que `asl-cle` le compose
 * côté serveur.
 *
 * # Champs de longueur fixe, aucun préfixe de longueur, aucune ambiguïté
 *
 * Les deux côtés doivent composer le même message ; un octet de différence et
 * aucune signature ne vérifie plus, sans qu'on sache pourquoi. Les domaines
 * séparent trois usages qui ne prouvent pas la même chose — s'authentifier,
 * prouver qu'on détient une clé, lier une attestation à cette clé — pour
 * qu'une signature faite pour l'un ne vaille jamais pour l'autre.
 */
object Messages {
    val DOMAINE_AUTHENTIFICATION: ByteArray = "air-service-locator/v1/authentification-machine".toByteArray() + 0
    val DOMAINE_POSSESSION: ByteArray = "air-service-locator/v1/possession-de-cle".toByteArray() + 0
    val DOMAINE_ATTESTATION: ByteArray = "air-service-locator/v1/attestation-d-appareil".toByteArray() + 0

    const val DEFI_OCTETS = 32
    const val LIAISON_OCTETS = 32
    const val CLE_OCTETS = 33
    const val SIGNATURE_OCTETS = 64

    /** `domaine ‖ genre (ASCII) ‖ identifiant (16) ‖ défi (32) ‖ liaison (32)` — ce que signe un appareil enrôlé pour s'authentifier. */
    fun aSigner(appareil: Identifiant, defi: ByteArray, liaison: ByteArray): ByteArray {
        require(appareil.genre == Genre.APPAREIL) { "seul un appareil signe ici" }
        require(defi.size == DEFI_OCTETS && liaison.size == LIAISON_OCTETS)
        return DOMAINE_AUTHENTIFICATION + appareil.genre.prefixe.code.toByte() + appareil.octets + defi + liaison
    }

    /** `domaine ‖ clé (33) ‖ défi (32) ‖ liaison (32)` — ce que signe celui qui présente une clé, avant d'avoir un nom. */
    fun dePossession(cle: ByteArray, defi: ByteArray, liaison: ByteArray): ByteArray {
        require(cle.size == CLE_OCTETS && defi.size == DEFI_OCTETS && liaison.size == LIAISON_OCTETS)
        return DOMAINE_POSSESSION + cle + defi + liaison
    }

    /** Même dessin, sous un troisième domaine : le nonce du jeton Play Integrity, pour lier l'attestation À LA clé présentée. */
    fun dAttestation(cle: ByteArray, defi: ByteArray, liaison: ByteArray): ByteArray {
        require(cle.size == CLE_OCTETS && defi.size == DEFI_OCTETS && liaison.size == LIAISON_OCTETS)
        return DOMAINE_ATTESTATION + cle + defi + liaison
    }
}
