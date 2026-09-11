package org.airdesktop.servicelocator.modele

import java.time.Instant

/** Un point d'écoute annoncé par un daemon : `(protocole, port)`. */
data class PointEcoute(val protocole: Protocole, val port: Int) {
    enum class Protocole(val libelle: String) { TCP("tcp"), UDP("udp") }

    val texte: String get() = "${protocole.libelle} $port"
}

/** Une adresse où l'on peut essayer de joindre un service, rendue IPv6 d'abord. */
data class Candidat(val protocole: PointEcoute.Protocole, val adresse: String, val port: Int, val origine: Origine) {
    enum class Origine {
        /** Le daemon le dit : vrai sur son réseau, souvent faux ailleurs. */
        ANNONCE,
        /** L'annuaire l'a OBSERVÉ sur la connexion d'annonce. */
        REFLEXIF,
    }
}

/**
 * Ce que l'annuaire affirme d'un point d'écoute, exactement — et jamais « en
 * ligne », qui confond « le daemon parle » avec « on peut l'atteindre »
 * (`docs/modele.md` §4.2).
 */
sealed interface Joignabilite {
    /** La connexion du daemon est tenue ; la sonde n'a pas encore conclu. */
    data object EnCours : Joignabilite
    /** L'annuaire a lui-même ouvert une connexion vers ce candidat, à cette date. */
    data class Joignable(val depuis: Instant, val candidat: String) : Joignabilite
    data class Injoignable(val depuis: Instant) : Joignabilite
    /** L'UDP ne se sonde pas : aucune poignée de main, aucun écho générique. */
    data object NonSonde : Joignabilite
}

/** Ce qu'un daemon annonce. Identifié par le couple (machine, nom). */
data class Service(
    val id: Identifiant,
    val nom: String,
    val points: List<PointEcoute>,
    val etat: Etat,
    val joignabilite: Map<PointEcoute, Joignabilite> = emptyMap(),
    val candidats: List<Candidat> = emptyList(),
    val oscille: Boolean = false,
) {
    /** La connexion EST le bail : elle est tenue, ou elle est fermée. */
    sealed interface Etat {
        data class Annonce(val depuis: Instant) : Etat
        /**
         * Proprement — le daemon l'a dit — ou par expiration du délai
         * d'inactivité. Un arrêt volontaire et une coupure n'appellent pas la
         * même réaction chez celui qui regarde.
         */
        data class Parti(val volontaire: Boolean, val le: Instant) : Etat
    }

    val pointsTexte: String get() = points.joinToString(" · ") { it.texte }

    /** Le verdict qui résume le service pour une liste : le meilleur des points TCP, sinon ce que l'état dit. */
    val resume: Joignabilite?
        get() {
            if (etat !is Etat.Annonce) return null
            val verdicts = points.mapNotNull { joignabilite[it] }
            return verdicts.firstOrNull { it is Joignabilite.Joignable }
                ?: verdicts.firstOrNull { it is Joignabilite.Injoignable }
                ?: verdicts.firstOrNull { it is Joignabilite.EnCours }
                ?: verdicts.firstOrNull()
        }
}
