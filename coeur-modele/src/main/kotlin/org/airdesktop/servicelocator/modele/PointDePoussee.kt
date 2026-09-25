package org.airdesktop.servicelocator.modele

/**
 * Le point de poussée qu'un distributeur UnifiedPush donne à cet appareil, et
 * que l'appareil dépose chez l'annuaire (`protocole.md` §2.2, « Le point de
 * poussée »).
 *
 * # LA MÊME FORME QUE CELLE QUE L'ANNUAIRE EXIGE, ET PAS PLUS
 *
 * `https://` et rien d'autre ; un nom DNS, jamais une adresse littérale ; le
 * port 443, implicite ou écrit ; ni identifiants ni fragment ; de l'ASCII
 * imprimable sans espace, 1024 octets au plus. Ce sont les règles de
 * `asl-api/src/point.rs`, recopiées : le banc les tient comme le serveur, et
 * l'écran peut dire POURQUOI un point est refusé — un ntfy auto-hébergé sur
 * `http://` ou sur le port 8080 est un cas réel, et « l'annuaire a répondu
 * 400 » n'apprendrait rien à qui l'a installé.
 *
 * **Ce n'est pas un analyseur d'URL**, et ce n'est pas lui qui décide : un
 * point qu'il laisse passer part tel quel, et c'est le `400` de l'annuaire qui
 * fait foi.
 */
object PointDePoussee {
    /** La seule plate-forme que l'annuaire accepte : `apns` et `fcm` rendent `400`. */
    const val PLATEFORME = "unifiedpush"

    /** Ce qu'un point peut faire, en octets — `POINT_MAX` du serveur. */
    const val OCTETS_MAX = 1024

    /** La règle que ce point enfreint, dans les mots de l'annuaire ; `null` s'il a la forme attendue. */
    fun refus(point: String): String? {
        if (point.length > OCTETS_MAX) return "1024 octets au plus"
        if (!point.all { it in '!'..'~' }) return "de l'ASCII imprimable, sans espace"
        if ('#' in point) return "pas de fragment"
        if (!point.startsWith("https://")) return "https:// et rien d'autre"
        val reste = point.removePrefix("https://")
        val autorite = reste.substring(0, reste.indexOfFirst { it == '/' || it == '?' }.takeIf { it >= 0 } ?: reste.length)
        if ('@' in autorite) return "pas d'identifiants"
        if (autorite.startsWith("[")) return "un nom DNS, pas une adresse"
        val hote = when (val deuxPoints = autorite.indexOf(':')) {
            -1 -> autorite
            else -> if (autorite.substring(deuxPoints + 1) == "443") autorite.substring(0, deuxPoints) else return "le port 443, implicite ou écrit"
        }
        return refusDeNom(hote)
    }

    /**
     * **La dernière étiquette décide** : `127.1`, `2130706433` ou `0x7f000001`
     * sont des adresses qu'un résolveur accepte, et toutes finissent par une
     * étiquette numérique — ce qu'aucun domaine public ne fait.
     */
    private fun refusDeNom(hote: String): String? {
        if (hote.isEmpty() || hote.length > 253) return "un nom DNS de 1 à 253 octets"
        val etiquettes = hote.split('.')
        val malFormee = etiquettes.any { e ->
            e.isEmpty() || e.length > 63 || !e.all { it.isAsciiLettreOuChiffre() || it == '-' } || e.startsWith('-') || e.endsWith('-')
        }
        if (malFormee) return "un nom DNS : lettres, chiffres, tirets, points"
        val derniere = etiquettes.last()
        val hexadecimale = (derniere.startsWith("0x") || derniere.startsWith("0X")) &&
            derniere.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (derniere.all { it in '0'..'9' } || hexadecimale) return "un nom DNS, pas une adresse"
        return null
    }

    private fun Char.isAsciiLettreOuChiffre() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /**
     * Le corps de `PUT /v1/appareils/{a}/poussee`.
     *
     * **Sans `cle` ni `secret`** : ce sont ceux de RFC 8291, que l'annuaire
     * range sans s'en servir — le message qu'il envoie est vide, il n'y a rien
     * à chiffrer. Les donner ne servirait à rien aujourd'hui, et le connecteur
     * UnifiedPush retenu (2.x) ne les produit pas.
     *
     * Écrit à la main plutôt qu'avec `org.json`, qui n'existe pas hors
     * d'Android : c'est ce qui permet à un essai JVM de dire que le corps est
     * le bon. Un point dont la forme tient n'a ni contrôle ni octet au-delà de
     * l'ASCII ; seuls `"` et `\` sont à échapper.
     */
    fun corps(point: String): String {
        val echappe = point.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"plateforme":"$PLATEFORME","point":"$echappe"}"""
    }
}
