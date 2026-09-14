package org.airdesktop.servicelocator.modele

import java.time.Instant

/**
 * Une arête entre deux comptes — jamais un jeton porteur. On voit à qui on a
 * donné, on retire à qui on veut, et retirer suffit : il n'y a rien à
 * récupérer (`docs/modele.md` §2.5).
 */
data class Autorisation(
    val id: Identifiant,
    val accordeePar: Identifiant,
    val accordeeA: Identifiant,
    val portee: Portee,
    /** Libre — pour savoir ce qu'on révoque six mois plus tard. */
    val etiquette: String,
    val accordeeLe: Instant,
    /** Révoquée, elle reste dans la liste, marquée : taire les révoquées ferait douter d'avoir cliqué. */
    val revoqueeLe: Instant? = null,
) {
    /**
     * Un seul champ, et le genre de l'identifiant la désigne — un objet
     * `{sorte, cible}` rendrait représentable une demande incohérente.
     */
    sealed interface Portee {
        data object Tout : Portee
        data class Machine(val id: Identifiant) : Portee
        data class Service(val id: Identifiant) : Portee
    }

    val estRevoquee: Boolean get() = revoqueeLe != null
}
