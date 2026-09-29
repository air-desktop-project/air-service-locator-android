package org.airdesktop.servicelocator.modele

import java.time.Instant

/**
 * L'état d'écho d'une machine (serveur 0.43.0, `docs/protocole.md` §3 quater) : ce que l'annuaire a constaté la
 * dernière fois qu'il a sondé l'`asl-echo` de la machine — dans `GET /v1/machines` et dans les machines de
 * `GET /v1/domaines/{d}`.
 *
 * # CE QUE L'ÉCHO PROUVE, ET CE QU'IL NE PROUVE PAS
 *
 * Un écho vérifié dit qu'une sonde signée est partie, et qu'une réponse signée **par la clé de cette machine** est
 * revenue. D'où elle est partie compte autant : de l'intérieur ([Depuis.Interieur] — l'annuaire local est sur la
 * machine ou sur son réseau), la preuve ne dit pas qu'on joint la machine du dehors. D'où les deux champs lus à part.
 *
 * # UN ANNUAIRE PLUS RÉCENT NE VIDE PAS L'ÉCRAN
 *
 * Chaque champ se lit seul, par sa clé : absent, d'une autre forme, c'est `null`. Un mot d'état qu'on ne connaît pas
 * devient [Etat.Inconnu] et se montre tel quel ([mot]) ; `echo_via` reste du texte, parce que `pcp` et `natpmp`
 * s'ajouteront (décision 96) sans que cette application ait à changer pour les dire.
 */
data class Echo(
    val etat: Etat,
    /** Le mot tel que l'annuaire l'a écrit — ce qu'on montre pour un [Etat.Inconnu]. */
    val mot: String,
    /** L'instant de la mesure (`echo_a`) ; absent tant qu'elle est en cours. */
    val a: Instant? = null,
    /** L'annuaire qui a sondé (`echo_par`). */
    val par: Identifiant? = null,
    /** D'où (`echo_depuis`) ; `null` pour un champ absent ou un mot qu'on ne connaît pas — on n'en conclut rien. */
    val depuis: Depuis? = null,
    /** Par où la preuve est arrivée (`echo_via`) : `upnp`, `nat`, `direct`, ou un mot plus récent. Du texte, exprès. */
    val via: String? = null,
) {
    enum class Etat(val mot: String) {
        Verifie("verifie"),
        Injoignable("injoignable"),
        /** Une réponse est venue, signée par une autre clé que celle de la machine : l'adresse n'est plus à elle. */
        AutreCle("autre_cle"),
        EnCours("en_cours"),

        /** Un mot d'un annuaire plus récent : on le montre, on n'en conclut rien. */
        Inconnu("");

        companion object {
            fun depuisMot(mot: String): Etat = entries.firstOrNull { it.mot == mot && it != Inconnu } ?: Inconnu
        }
    }

    enum class Depuis(val mot: String) {
        /** Sondé du dehors : par une racine, ou par un annuaire qui n'est pas sur le réseau de la machine. */
        Exterieur("exterieur"),
        /** Sondé de son réseau — la règle de `sonde_locale` (décision 60). */
        Interieur("interieur");

        companion object {
            /** `null` pour un champ absent comme pour un mot qu'on ne connaît pas. */
            fun depuisMot(mot: String?): Depuis? = entries.firstOrNull { it.mot == mot }
        }
    }
}
