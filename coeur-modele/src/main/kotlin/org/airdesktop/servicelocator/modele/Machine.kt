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
    /** Libre, pour l'humain. 1 à 64 octets, tout l'UTF-8 sauf `"`, `\` et les contrôles. */
    val nom: String,
    val capacites: Set<Capacite>,
    val cle: Cle,
    val services: List<Service> = emptyList(),
) {
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
        /** Le nom respecte-t-il les règles de `docs/modele.md` §2.3 ? */
        fun nomValide(nom: String): Boolean {
            val octets = nom.toByteArray(Charsets.UTF_8).size
            if (octets !in 1..64) return false
            var i = 0
            while (i < nom.length) {
                val point = nom.codePointAt(i)
                val refuse = point == '"'.code || point == '\\'.code ||
                    point < 0x20 || point == 0x7F ||            // C0 et DEL
                    point in 0x80..0x9F ||                      // C1
                    point == 0xFEFF ||                          // marque d'ordre
                    point in 0x202A..0x202E ||                  // forceurs de sens
                    point in 0x2066..0x2069
                if (refuse) return false
                i += Character.charCount(point)
            }
            return true
        }
    }
}
