package org.airdesktop.servicelocator.reseau

/**
 * Les symboles JNI d'`asl-client-android` — la voie mobile d'`asl-client`,
 * exportée depuis Rust.
 *
 * **Ce fichier est une transcription, et rien d'idiomatique.** Chaque
 * `external fun` est le nom JNI d'un symbole `Java_org_airdesktop_servicelocator_reseau_Natif_*`
 * du dépôt client : déplacer cet objet, ou le renommer, casse la liaison —
 * et c'est voulu, un objet natif n'a qu'un seul propriétaire. Ce qui est
 * idiomatique vit dans [AnnuaireReel].
 *
 * Un handle est un `Long`, zéro quand il n'existe pas. Ce qui rend `null` a
 * échoué, et [dernierCode] dit pourquoi ; ce qui rend un `Int` rend le code.
 */
object Natif {
    init {
        System.loadLibrary("asl_client_android")
    }

    /** Ce que l'application pose pour signer : `null` si le porteur n'a pas signé. */
    fun interface Signataire {
        fun signer(message: ByteArray): ByteArray?
    }

    external fun neuf(): Long
    external fun libere(h: Long)
    external fun annuaire(h: Long, adresse: String, nom: String): Int
    external fun racines(h: Long, pem: ByteArray): Int
    external fun cle(h: Long, cle: ByteArray, signataire: Signataire): Int
    external fun identite(h: Long, identifiant: String): Int
    external fun connecter(h: Long): Int
    external fun deconnecter(h: Long): Int
    external fun dernierCode(h: Long): Int
    external fun liaison(h: Long): ByteArray?
    external fun defi(h: Long): ByteArray?
    external fun messagePourAttestation(h: Long): ByteArray?
    /** `[compte, appareil]`, ou `null`. */
    external fun creerCompte(h: Long, plateforme: Int, attestation: ByteArray?): Array<String>?
    /** `statut (2 octets) ‖ corps`, ou `null` si la requête n'a pas pu partir. */
    external fun requete(h: Long, methode: String, chemin: String, corps: ByteArray?): ByteArray?
    external fun identifiant(h: Long): String?
    external fun fauteTexte(code: Int): String
    /** Ce que la bibliothèque sait dire d'elle-même ici — pour le débogage. */
    external fun diagnostic(): String

    // Les codes, tels que `asl.h` les définit.
    const val OK = 0
    const val ARGUMENT = -1
    const val CONFIGURATION = -2
    const val INJOIGNABLE = -3
    const val REFUSE = -4
    const val INTERNE = -6
    const val PAS_D_IDENTITE = -7
    const val NON_CONNECTE = -10
    const val SIGNATURE_REFUSEE = -11

    const val PLATEFORME_AUCUNE = 0
}
