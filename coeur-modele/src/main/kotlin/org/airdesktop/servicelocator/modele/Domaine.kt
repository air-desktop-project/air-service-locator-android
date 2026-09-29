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
    /**
     * Le domaine racine R (décision 43) — `"sorte":"racine"` sur son objet, lui seul (serveur 0.39.0). Faux pour un
     * domaine ordinaire, et pour tout ce qu'un annuaire plus ancien rend : il n'écrit pas ce champ.
     */
    val racine: Boolean = false,
) {
    /** Ce qu'on montre d'abord : l'alias quand il existe, l'identifiant sinon. */
    val affiche: String get() = alias ?: id.texte

    fun peut(droit: String): Boolean = droit in droits || (droit != LOCALISER && ADMINISTRER in droits)

    /**
     * Y ranger une machine : `rattacher` **tenu en propre**, dans la liste que l'annuaire rend — pas emporté par
     * `administrer`, ni par la propriété.
     *
     * **C'EST L'ANNUAIRE QUI DIT SI R REÇOIT DES MACHINES.** Jusqu'au serveur 0.38, le domaine racine n'en reçoit
     * aucune : l'annuaire ne rend à son propriétaire que `["administrer"]`, et un `PUT /v1/machines/{m}/domaine` qui
     * le vise rend `404`. Or [peut] fait descendre `rattacher` de `administrer` : s'y fier ici proposerait R à
     * ranger. Un domaine ordinaire, lui, porte toujours `rattacher` en toutes lettres — les quatre droits pour son
     * propriétaire, `["administrer","rattacher","voir"]` pour son groupe d'administrateurs (`protocole.md`,
     * `GET /v1/domaines`). À partir de 0.39.0, R porte les quatre droits et reçoit des machines : la même règle l'y
     * propose alors, sans qu'on ait à le distinguer — c'est voulu.
     */
    val admetUneMachine: Boolean get() = RATTACHER in droits

    /**
     * Le confier à un annuaire local, ou le rendre aux racines. **R ne se confie jamais** : c'est lui qui fonde les
     * racines. Tant qu'il ne tenait que `administrer`, [admetUneMachine] suffisait à l'écarter ; depuis qu'il tient
     * les quatre droits (0.39.0), il ne se reconnaît plus qu'à [racine].
     */
    val seConfie: Boolean get() = !racine

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
    /** Ce que ce membre conclut de sa paire (décision 70) ; `null` tant qu'il ne l'a pas dit à cette racine. */
    val paire: EtatDeLaPaire? = null,
    /** Sa voie de fédération vers la racine qui répond (décision 86) ; `null` si elle n'en a rien vu depuis son démarrage. */
    val voie: VoieDuMembre? = null,
) {
    /** Le titulaire — celui que l'on nomme pour retirer l'annuaire, lui confier un domaine, lui ajouter un second. */
    val estTitulaire: Boolean get() = membre != null && membre == annuaire
}

/** Le code d'inscription à taper sur la machine (`asl-server --register <code>`) : dix symboles, vingt-quatre heures. */
data class CodeDInscription(val code: String, val expireA: Instant)

/**
 * Ce qu'un membre d'annuaire local conclut de sa paire — le champ `paire` de `GET /v1/annuaires` (0.36.0, décision
 * 70 ; `protocole.md` §3 ter). Le mot du fil, en ASCII, à l'identique.
 *
 * [SansPeer] et [PeerInconnu] sont les deux façons dont une paire **ne se réplique pas** : l'une parce que ce membre
 * tourne sans `--peer`, l'autre parce que son `--peer` ne désigne aucun autre membre accepté. Le serveur ne refuse pas
 * de démarrer ; c'est l'application qui doit le montrer.
 */
enum class EtatDeLaPaire(val mot: String) {
    /** Un seul membre accepté : il n'a personne à nommer. */
    Seul("seul"),
    Reglee("reglee"),
    SansPeer("sans-peer"),
    PeerInconnu("peer-inconnu"),

    /** Un mot d'un annuaire plus récent : on n'en conclut rien — ni coche, ni alarme. */
    Inconnu("");

    /** Les deux états qui empêchent la paire de se répliquer. */
    val malReglee: Boolean get() = this == SansPeer || this == PeerInconnu

    companion object {
        /** `null` pour un champ absent ; [Inconnu] pour un mot qu'on ne connaît pas. */
        fun depuisMot(mot: String?): EtatDeLaPaire? =
            if (mot.isNullOrEmpty()) null else entries.firstOrNull { it.mot == mot && it != Inconnu } ?: Inconnu
    }
}

/**
 * La voie de fédération d'un membre vers **la racine qui répond** — le champ `voie` de `GET /v1/annuaires` (0.38.0,
 * décision 86 ; `annuaires.md` §2 quinquies). Une chaîne, pas un booléen, pour que les clients d'hier la sautent.
 *
 * L'état vivant ne s'écrit pas : une racine qui redémarre ne distingue pas « tombée » de « pas encore revenue », et
 * n'envoie alors rien — ce que `null` porte ici. On ne le confond pas avec [Tombee].
 */
enum class VoieDuMembre(val mot: String) {
    /** Elle tient : le membre a prouvé sa clé et parlé depuis moins de trente secondes. */
    Ouverte("ouverte"),

    /** Elle a tenu depuis que cette racine tourne, et s'est tue. */
    Tombee("tombee"),

    /** Un mot d'un annuaire plus récent : on n'en conclut rien. */
    Inconnue("");

    companion object {
        /** `null` pour un champ absent ; [Inconnue] pour un mot qu'on ne connaît pas. */
        fun depuisMot(mot: String?): VoieDuMembre? =
            if (mot.isNullOrEmpty()) null else entries.firstOrNull { it.mot == mot && it != Inconnue } ?: Inconnue
    }
}

/**
 * L'état d'un annuaire local tel que la racine qui répond le voit — la règle de l'`asl-directory` (décision 86) :
 * **vivant** si l'un de ses membres a sa voie ouverte, **parti** si aucun ne l'a et qu'au moins une est tombée, **pas
 * de nouvelles** sinon — la racine vient de redémarrer, ou aucun membre ne lui a parlé depuis. Une voie au mot inconnu
 * ne compte ni pour l'un ni pour l'autre.
 *
 * « Vivant » dit qu'une voie sortante tient, pas que la maison est joignable du dehors (décision 83).
 */
enum class EtatDeLAnnuaire {
    Vivant,
    Parti,
    PasDeNouvelles;

    companion object {
        fun de(membres: List<Inscription>): EtatDeLAnnuaire {
            val voies = membres.mapNotNull { it.voie }
            return when {
                VoieDuMembre.Ouverte in voies -> Vivant
                VoieDuMembre.Tombee in voies -> Parti
                else -> PasDeNouvelles
            }
        }
    }
}
