package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La racine que la connexion tenue a effectivement jointe : son adresse, telle
 * que la connexion la connaît (`asl_appareil_distante`), et son nom quand la
 * liste des racines permet de le dire.
 *
 * **Sous « Automatique », c'est la seule façon de le savoir.** L'alias rend les
 * adresses des deux racines, la tournée garde la première qui répond, et
 * l'annuaire ne journalise pas les connexions (C13). Rien ne dépend de ce nom :
 * une racine qu'on ne sait pas nommer se dit par son adresse.
 */
data class RacineJointe(val adresse: String, val nom: String?) {
    /** Ce que l'écran montre : le nom s'il est connu, sinon l'adresse brute. */
    val affichee: String get() = nom ?: adresse

    companion object {
        /**
         * Le nom de la racine qui contient [adresse] : **l'entrée la plus
         * précise** — celle qui a le moins d'adresses. Un alias qui couvre les
         * deux racines contient toutes les adresses, et le dire ne renseignerait
         * rien : argon, qui n'a que les siennes, est la réponse. L'alias
         * seulement faute de mieux ; `null` si aucune entrée ne la contient.
         * L'ordre de la liste ne compte pas.
         *
         * La même règle que l'application iOS (`RacineJointe.nommer`), pour que
         * les deux disent la même chose de la même connexion.
         */
        fun nommer(adresse: String, parmi: List<Pair<String, List<String>>>): String? =
            parmi.filter { (_, adresses) -> adresse in adresses }
                .minByOrNull { (_, adresses) -> adresses.size }
                ?.first
    }
}

/**
 * Ce qu'un annuaire sait de sa connexion, tenu à jour par lui et lu par l'écran.
 *
 * **Deux événements seulement** : une connexion aboutit ([jointe]), une
 * connexion se perd ([perdue]) — tombée, fermée, handle libéré. Entre les
 * deux, l'état ne bouge pas, et le lire ne demande rien : ni requête, ni
 * empreinte.
 */
class SuiviDeLaRacine(initiale: RacineJointe? = null) {
    private val etat = MutableStateFlow(initiale)

    /** `null` : aucune connexion tenue. */
    val racine: StateFlow<RacineJointe?> = etat.asStateFlow()

    /**
     * La connexion a abouti sur [adresse]. [connues] : chaque racine de la
     * liste, avec les adresses qu'elle résout — pour la nommer.
     */
    fun jointe(adresse: String, connues: List<Pair<String, List<String>>>) {
        etat.value = RacineJointe(adresse, RacineJointe.nommer(adresse, connues))
    }

    /** Plus de connexion tenue. */
    fun perdue() {
        etat.value = null
    }
}
