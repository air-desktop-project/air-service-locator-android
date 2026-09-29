package org.airdesktop.servicelocator.modele

import java.time.Instant

/**
 * Ce qu'une machine a le droit de faire. **Rien n'est coché d'avance** : une
 * machine qui porte les deux a un rayon de dégât plus large qu'une machine qui
 * n'en porte qu'une (`docs/modele.md` §2.3).
 */
enum class Capacite(val libelle: String) {
    /** Ses daemons peuvent annoncer et rafraîchir des services. */
    ANNONCE("annonce"),
    /** Elle peut demander où joindre un service — les siens, et ceux accordés. */
    LECTURE("lecture"),
}

/** Une machine déclarée depuis l'application — celle qui sert un service comme celle qui le consomme. */
data class Machine(
    val id: Identifiant,
    /**
     * Son nom d'hôte (0.26.0, décision 47) : lettres ASCII, chiffres, tiret, rangé en minuscules — voir
     * [nomDHote]. Un nom rangé avant 0.26.0 peut être du texte libre : il s'affiche tel quel.
     */
    val nom: String,
    val capacites: Set<Capacite>,
    val cle: Cle,
    val services: List<Service> = emptyList(),
    /** Du texte choisi, indépendant du nom et du domaine — voir [Alias.pourMachine]. Absent tant qu'on n'en pose pas. */
    val alias: String? = null,
    /** Son état d'écho (serveur 0.43.0) ; `null` : l'annuaire n'en dit rien — pas d'`asl echo`, ou un annuaire d'avant. */
    val echo: Echo? = null,
) {
    /** Ce qu'on montre d'abord : l'alias quand il existe, le nom sinon. */
    val affichee: String get() = alias ?: nom

    /**
     * Ce que l'annuaire sait de la clé Ed25519 de la machine.
     *
     * # Ce que l'annuaire dit, et ce que cet appareil sait en plus
     *
     * L'annuaire rend deux états — `attendue`, `enrolee` — et rien d'autre : il
     * ne range ni horodatage, ni le code en cours (gardé par son empreinte
     * seulement), ni la trace d'une révocation. Le code n'est donc connu que du
     * téléphone qui l'a fait émettre ; la date d'enrôlement, la révocation, de
     * celui qui a agi. D'où les `null` : « cet appareil ne le sait pas », jamais
     * « ça n'a pas eu lieu ».
     */
    sealed interface Cle {
        /**
         * Déclarée, pas encore enrôlée : la clé n'existe pas encore. Elle sera
         * générée SUR la machine, et sa partie privée n'en sortira jamais. Le
         * code est celui émis d'ici, s'il y en a un.
         */
        data class Attendue(val code: CodeEnrolement? = null) : Cle
        data class Enrolee(val le: Instant? = null) : Cle
        /**
         * Révoquée depuis l'application : connexions fermées, baux tombés. La
         * machine reste — nom, capacités, services — et attend un nouveau code.
         * L'annuaire ne distingue pas une clé révoquée d'une clé jamais posée ;
         * seul l'appareil qui a révoqué le sait, et depuis quand.
         */
        data class Revoquee(val le: Instant, val code: CodeEnrolement? = null) : Cle
    }

    val capacitesTexte: String get() = Capacite.entries.filter { it in capacites }.joinToString(", ") { it.libelle }

    /** Deux daemons du même nom se chassent l'un l'autre : la date d'annonce oscille, et l'application le signale. */
    val unServiceOscille: Boolean get() = services.any { it.oscille }

    companion object {
        /** Un nom d'hôte fait au plus une étiquette DNS (RFC 1123 §2.1). */
        const val NOM_OCTETS_MAX = 63

        /**
         * Le nom tel que l'annuaire le rangera — en minuscules —, ou `null` s'il le refuserait.
         *
         * # UN NOM D'HÔTE, PAS UN TEXTE LIBRE (0.26.0, décision 47)
         *
         * Lettres ASCII, chiffres, tiret, un à soixante-trois octets, ni tiret en tête ni en queue :
         * ce qu'accepte `hostname`, et ce qu'on écrit devant un domaine sans l'encoder. Le DNS
         * compare sans casse ; l'annuaire range donc une forme, la minuscule, et « Grenier » devient
         * « grenier ». Le texte libre — accents, espaces, émoji — va dans l'[alias].
         */
        fun nomDHote(nom: String): String? {
            if (nom.isEmpty() || nom.length > NOM_OCTETS_MAX) return null
            if (nom.first() == '-' || nom.last() == '-') return null
            if (!nom.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }) return null
            return nom.lowercase()
        }

        /** L'annuaire accepterait-il ce nom ? */
        fun nomValide(nom: String): Boolean = nomDHote(nom) != null
    }
}

/**
 * Une machine d'un AUTRE compte, telle qu'une autorisation la donne à voir (`docs/protocole.md` §2.2,
 * `GET /v1/utilisateurs/{u}/machines`) : son identifiant et son nom — rien d'autre, ni capacités, ni clé, ni code,
 * qui n'appartiennent qu'au propriétaire.
 */
data class MachineVisible(val id: Identifiant, val nom: String, val alias: String? = null) {
    /** L'alias quand il existe, le nom sinon — comme [Machine.affichee]. */
    val affichee: String get() = alias ?: nom
}
