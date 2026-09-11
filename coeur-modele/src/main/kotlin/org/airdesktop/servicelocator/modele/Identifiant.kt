package org.airdesktop.servicelocator.modele

/**
 * Ce qu'un identifiant désigne. Le genre est porté par le préfixe, et il est
 * vérifié à la lecture : un identifiant de machine placé là où l'on attend un
 * service est une erreur, pas une valeur à interpréter.
 */
enum class Genre(val prefixe: Char) {
    UTILISATEUR('u'), APPAREIL('a'), MACHINE('m'), SERVICE('s'), AUTORISATION('g'), ANNUAIRE('n');

    companion object {
        fun depuisPrefixe(c: Char): Genre? = entries.firstOrNull { it.prefixe == c.lowercaseChar() }
    }
}

/**
 * Un identifiant public d'air-service-locator : `u-` + 26 symboles.
 *
 * # Il se compare sur ses seize octets, jamais comme une chaîne
 *
 * L'alphabet est le base32 de Crockford (`docs/modele.md` §2, `asl-id` côté
 * serveur) : il retire `I`, `L`, `O` et `U`, et **rattrape la faute à la
 * lecture** — `I` et `L` valent `1`, `O` vaut `0`. Plusieurs textes désignent
 * donc le même identifiant, et un `==` sur des chaînes conclurait à tort qu'il
 * s'agit de deux machines. C'est pourquoi cette classe porte les octets, et ne
 * rend le texte qu'à l'affichage, toujours sous sa forme canonique.
 */
class Identifiant(val genre: Genre, octets: ByteArray) {
    /** Seize octets, gros-boutiens — 128 bits, qui ne se devinent pas. */
    val octets: ByteArray = octets.copyOf()

    init {
        require(octets.size == 16) { "un identifiant porte seize octets" }
    }

    sealed class Erreur(message: String) : Exception(message) {
        class Longueur(val attendue: Int, val obtenue: Int) : Erreur("longueur $obtenue, attendue $attendue")
        object PrefixeInconnu : Erreur("préfixe inconnu")
        object SeparateurAbsent : Erreur("séparateur absent")
        class SymboleInvalide(val position: Int) : Erreur("symbole invalide en position $position")
        /** Le premier symbole vaut 8 ou plus : 130 bits ne tiennent pas en 128. */
        object Debordement : Erreur("débordement")
        class GenreInattendu(val attendu: Genre, val obtenu: Genre) : Erreur("genre $obtenu, attendu $attendu")
    }

    /** Le texte canonique : préfixe minuscule, tiret, corps en majuscules. */
    val texte: String by lazy {
        // 128 bits lus par groupes de 5, de droite à gauche : le premier symbole
        // ne porte que 3 bits, ce qui est la raison du débordement à la lecture.
        val bits = BooleanArray(130)
        for (i in 0 until 16) for (b in 0 until 8) bits[2 + i * 8 + b] = (octets[i].toInt() shr (7 - b)) and 1 == 1
        val corps = CharArray(NOMBRE_SYMBOLES) { position ->
            var indice = 0
            for (b in 0 until 5) indice = (indice shl 1) or (if (bits[position * 5 + b]) 1 else 0)
            ALPHABET[indice]
        }
        "${genre.prefixe}-${String(corps)}"
    }

    /** Une forme courte pour les listes : `u-3F8K…Q2W7`. Jamais pour comparer. */
    val abrege: String get() = texte.take(6) + "…" + texte.takeLast(4)

    override fun equals(other: Any?) = other is Identifiant && other.genre == genre && other.octets.contentEquals(octets)
    override fun hashCode() = 31 * genre.hashCode() + octets.contentHashCode()
    override fun toString() = texte

    companion object {
        /** `0123456789ABCDEFGHJKMNPQRSTVWXYZ` — sans `I`, `L`, `O`, `U`. */
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LONGUEUR_TEXTE = 28
        const val NOMBRE_SYMBOLES = 26

        /** La valeur d'un symbole de Crockford, casse indifférente, fautes rattrapées. */
        fun valeur(c: Char): Int? = when (val maj = c.uppercaseChar()) {
            'I', 'L' -> 1
            'O' -> 0
            else -> ALPHABET.indexOf(maj).takeIf { it >= 0 }
        }

        /**
         * Lit un identifiant, quel que soit son genre. La casse est indifférente,
         * et les confusions de Crockford sont rattrapées.
         */
        fun analyser(texte: String): Identifiant {
            if (texte.length != LONGUEUR_TEXTE) throw Erreur.Longueur(LONGUEUR_TEXTE, texte.length)
            val genre = Genre.depuisPrefixe(texte[0]) ?: throw Erreur.PrefixeInconnu
            if (texte[1] != '-') throw Erreur.SeparateurAbsent
            val bits = BooleanArray(130)
            texte.substring(2).forEachIndexed { position, c ->
                val chiffre = valeur(c) ?: throw Erreur.SymboleInvalide(position)
                for (b in 0 until 5) bits[position * 5 + b] = (chiffre shr (4 - b)) and 1 == 1
            }
            // Les deux bits de tête n'ont pas de place dans seize octets.
            if (bits[0] || bits[1]) throw Erreur.Debordement
            val octets = ByteArray(16) { i ->
                var v = 0
                for (b in 0 until 8) v = (v shl 1) or (if (bits[2 + i * 8 + b]) 1 else 0)
                v.toByte()
            }
            return Identifiant(genre, octets)
        }

        /** Lit un identifiant en **exigeant** son genre — la forme à préférer partout où le genre est connu. */
        fun analyser(texte: String, attendu: Genre): Identifiant {
            val identifiant = analyser(texte)
            if (identifiant.genre != attendu) throw Erreur.GenreInattendu(attendu, identifiant.genre)
            return identifiant
        }
    }
}
