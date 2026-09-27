package org.airdesktop.servicelocator.modele

import java.time.Instant

/**
 * Ce qu'un annuaire sait faire, selon la version qu'il dit (`GET /v1/version`).
 *
 * # DANS LE DOUTE, ON NE PROPOSE PAS
 *
 * Une version illisible — « banc en mémoire », absente — ne passe aucun seuil : proposer un écran dont l'annuaire
 * refuserait chaque verbe ferait croire à une panne. C'est la règle de l'alias de machine (0.26.0), étendue.
 */
object Versions {
    /** Les domaines, leur alias, le rattachement des machines (serveur 0.23.0). */
    const val DOMAINES = 23

    /** L'annuaire local, son inscription, l'hébergement d'un domaine, l'administration des racines (serveur 0.27.0). */
    const val ANNUAIRES_LOCAUX = 27

    /** Vrai pour une version semver `0.<mineur>.x` ou plus ; faux pour une version illisible. */
    fun auMoins(version: String?, mineur: Int): Boolean {
        val parties = version?.split('.')?.takeIf { it.size == 3 }?.map { it.toIntOrNull() ?: return false } ?: return false
        val (majeurLu, mineurLu, _) = parties
        return majeurLu > 0 || mineurLu >= mineur
    }
}

/**
 * Un domaine (`docs/modele.md` §2.11) : ce que le compte possède — de un à n —, ou ce où l'un de ses groupes tient
 * un droit. Il rassemble des machines ; il ne contient pas le compte.
 */
data class Domaine(
    val id: Identifiant,
    val proprietaire: Identifiant,
    /** Du texte choisi, public et non unique — voir [Alias.pourDomaine]. */
    val alias: String?,
    /** L'annuaire local qui le tient, ou `null` : les racines. */
    val hebergePar: Identifiant?,
    /** L'union de ce que ce compte peut sur lui : `administrer`, `rattacher`, `voir`, `localiser`. */
    val droits: Set<String>,
) {
    /** Ce qu'on montre d'abord : l'alias quand il existe, l'identifiant sinon. */
    val affiche: String get() = alias ?: id.texte

    fun peut(droit: String): Boolean = droit in droits || (droit != LOCALISER && ADMINISTRER in droits)

    /** Y ranger une machine : `rattacher`, reçu ou emporté par `administrer`. */
    val admetUneMachine: Boolean get() = peut(RATTACHER)

    companion object {
        const val ADMINISTRER = "administrer"
        const val RATTACHER = "rattacher"
        const val VOIR = "voir"
        const val LOCALISER = "localiser"
    }
}

/** Une machine rattachée à un domaine, telle que `GET /v1/domaines/{d}` la montre. Le nom n'est rendu qu'à qui le voit. */
data class MachineDuDomaine(val machine: Identifiant, val proprietaire: Identifiant, val nom: String?, val alias: String?) {
    val affichee: String get() = alias ?: nom ?: machine.texte
}

/** Un domaine et les machines qu'on y voit. */
data class DetailDuDomaine(val domaine: Domaine, val machines: List<MachineDuDomaine>)

/**
 * Où en est l'inscription d'un annuaire local (`protocole.md` §2.2) — le mot du fil, à l'identique.
 *
 * [Attendue] : un code déclaré, pas encore présenté ; les autres suivent la présentation.
 */
enum class EtatDInscription(val mot: String) {
    Attendue("attendue"),
    EnAttente("en attente"),
    Acceptee("acceptée"),
    Refusee("refusée"),
    Retiree("retirée"),

    /** Un mot qu'on ne connaît pas vient d'un annuaire plus récent : on le montre tel quel, sans rien supposer. */
    Inconnu("");

    companion object {
        fun depuisMot(mot: String?): EtatDInscription = entries.firstOrNull { it.mot == mot && it != Inconnu } ?: Inconnu
    }
}

/**
 * Une inscription d'annuaire local : une ligne de `GET /v1/annuaires` (mes annuaires) ou de `GET /v1/inscriptions`
 * (celles qui attendent un administrateur des racines).
 */
data class Inscription(
    val etat: EtatDInscription,
    /** La mot tel que l'annuaire l'a dit — ce qu'on montre si [etat] est [EtatDInscription.Inconnu]. */
    val motDeLEtat: String,
    /** Le membre `n-…` — la machine qui s'est présentée ; absent tant que le code attend. */
    val membre: Identifiant?,
    /** L'annuaire — son titulaire, le premier membre accepté. */
    val annuaire: Identifiant?,
    /** Le propriétaire, pour un administrateur des racines seulement. */
    val proprietaire: Identifiant?,
    val adresse: String,
    /** Jusqu'à quand le code se présente, pour une déclaration attendue. */
    val expireA: Instant?,
) {
    /** Le titulaire — celui que l'on nomme pour retirer l'annuaire, lui confier un domaine, lui ajouter un second. */
    val estTitulaire: Boolean get() = membre != null && membre == annuaire
}

/** Le code d'inscription à taper sur la machine (`asl-server --register <code>`) : dix symboles, vingt-quatre heures. */
data class CodeDInscription(val code: String, val expireA: Instant)
