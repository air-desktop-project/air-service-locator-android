package org.airdesktop.servicelocator.modele

/**
 * Ce que deux téléphones s'échangent pour enrôler le second
 * (`docs/protocole.md` §2.2, `POST /v1/appareils`).
 *
 * # Pourquoi deux messages, et dans ce sens
 *
 * L'annuaire n'enrôle un appareil de plus que **sur la demande d'un appareil
 * déjà enrôlé** : c'est lui qui poste la clé du nouveau. La clé ne peut venir
 * que du nouveau téléphone — elle est née dans son matériel — et l'identifiant
 * rendu ne peut venir que de l'ancien — c'est à lui que l'annuaire l'a dit.
 * D'où l'aller-retour, d'un écran à une caméra :
 *
 * 1. le nouveau montre [Cle] ;
 * 2. l'ancien la lit, la poste, et montre [Appareil] ;
 * 3. le nouveau la lit, et prouve sa clé sur sa propre connexion.
 *
 * Rien de secret ne passe : une clé publique, deux identifiants. Ce qui
 * prouve, c'est la signature que seul le nouveau saura produire ensuite.
 *
 * # La forme
 *
 * Un texte, lisible à voix haute au besoin, préfixé pour qu'un lecteur de QR
 * sache ce qu'il tient : `asl:cle:` puis cinquante-trois symboles de Crockford
 * (33 octets) ; `asl:appareil:` puis `u-…:a-…`. Même alphabet que les
 * identifiants, mêmes confusions rattrapées.
 */
sealed interface Invitation {
    /** Du nouveau vers l'ancien : la clé publique du nouveau, SEC1 compressée. */
    class Cle(val octets: ByteArray) : Invitation {
        override fun equals(other: Any?) = other is Cle && other.octets.contentEquals(octets)
        override fun hashCode() = octets.contentHashCode()
    }

    /** De l'ancien vers le nouveau : le compte rejoint, et l'identifiant que l'annuaire a donné au nouvel appareil. */
    data class Appareil(val compte: Identifiant, val appareil: Identifiant) : Invitation

    val texte: String
        get() = when (this) {
            is Cle -> PREFIXE_CLE + Crockford.texte(octets)
            is Appareil -> PREFIXE_APPAREIL + compte.texte + ":" + appareil.texte
        }

    companion object {
        const val PREFIXE_CLE = "asl:cle:"
        const val PREFIXE_APPAREIL = "asl:appareil:"

        /** Lit une invitation, ou rend `null` : un QR étranger, une faute de frappe. */
        fun analyser(texte: String): Invitation? {
            val propre = texte.trim()
            val minuscules = propre.lowercase()
            if (minuscules.startsWith(PREFIXE_CLE)) {
                val octets = Crockford.octets(propre.substring(PREFIXE_CLE.length), Messages.CLE_OCTETS) ?: return null
                if (octets[0] != 0x02.toByte() && octets[0] != 0x03.toByte()) return null
                return Cle(octets)
            }
            if (minuscules.startsWith(PREFIXE_APPAREIL)) {
                val morceaux = propre.substring(PREFIXE_APPAREIL.length).split(":")
                if (morceaux.size != 2) return null
                val compte = runCatching { Identifiant.analyser(morceaux[0], Genre.UTILISATEUR) }.getOrNull() ?: return null
                val appareil = runCatching { Identifiant.analyser(morceaux[1], Genre.APPAREIL) }.getOrNull() ?: return null
                return Appareil(compte, appareil)
            }
            return null
        }
    }
}

/**
 * Le base32 de Crockford sur un nombre d'octets quelconque — l'alphabet des
 * identifiants, étendu à une clé.
 *
 * Les bits sont lus par groupes de cinq **de droite à gauche**, comme pour un
 * identifiant : le premier symbole ne porte que le reste, et complète à zéro.
 * Ce qui n'est pas un multiple de cinq n'est donc jamais ambigu à la lecture.
 */
object Crockford {
    fun texte(octets: ByteArray): String {
        val symboles = (octets.size * 8 + 4) / 5
        val bourrage = symboles * 5 - octets.size * 8
        val bits = BooleanArray(symboles * 5)
        for (i in octets.indices) for (b in 0 until 8) bits[bourrage + i * 8 + b] = (octets[i].toInt() shr (7 - b)) and 1 == 1
        return String(CharArray(symboles) { position ->
            var indice = 0
            for (b in 0 until 5) indice = (indice shl 1) or (if (bits[position * 5 + b]) 1 else 0)
            Identifiant.ALPHABET[indice]
        })
    }

    /** Les octets, exactement `compte`, ou `null` : mauvaise longueur, symbole hors alphabet, ou des bits de bourrage qui ne sont pas à zéro. */
    fun octets(texte: String, compte: Int): ByteArray? {
        val symboles = (compte * 8 + 4) / 5
        if (texte.length != symboles) return null
        val bits = BooleanArray(symboles * 5)
        for ((position, c) in texte.withIndex()) {
            val valeur = Identifiant.valeur(c) ?: return null
            for (b in 0 until 5) bits[position * 5 + b] = (valeur shr (4 - b)) and 1 == 1
        }
        val bourrage = symboles * 5 - compte * 8
        if ((0 until bourrage).any { bits[it] }) return null
        return ByteArray(compte) { i ->
            var octet = 0
            for (b in 0 until 8) octet = (octet shl 1) or (if (bits[bourrage + i * 8 + b]) 1 else 0)
            octet.toByte()
        }
    }
}
