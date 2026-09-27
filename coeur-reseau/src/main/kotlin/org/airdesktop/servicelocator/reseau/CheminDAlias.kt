package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Alias

/**
 * Le chemin qui résout cet alias de compte, en NFC.
 *
 * # DEUX FORMES, POUR DEUX ANNUAIRES
 *
 * `/v1/alias/{alias}` est servi par toutes les versions, et depuis 0.26.0 il admet les majuscules ASCII ; un alias en
 * ASCII qui ne porte aucun caractère qu'un chemin ne peut pas contenir tel quel (`/`, `?`, `%`, `#`, espace) le prend.
 * Tout autre alias — accentué, avec une espace — prend `/v1/alias?alias=…`, pourcent-encodé, que seule une 0.26.0
 * connaît : une 0.25.0 répondra qu'elle ne le trouve pas, ce qui est vrai — elle ne pouvait pas le tenir.
 */
fun cheminDAlias(alias: String): String {
    val forme = Alias.nfc(alias)
    val dansLeChemin = forme.isNotEmpty() && forme.all { it.code in 0x21..0x7E && it !in "/?%#" }
    return if (dansLeChemin) "/v1/alias/$forme" else "/v1/alias?alias=${pourcentEncoder(forme)}"
}

/**
 * L'encodage pourcent de RFC 3986 : chaque octet UTF-8 hors des caractères non réservés (`A-Z a-z 0-9 - . _ ~`)
 * devient `%XX`.
 *
 * **Pas `URLEncoder`**, qui encode pour les formulaires : il écrit une espace `+`, et l'annuaire ne lit pas `+` comme
 * une espace (`protocole.md`, `GET /v1/alias?alias=`) — « a b » deviendrait l'alias « a+b ».
 */
fun pourcentEncoder(texte: String): String = buildString {
    for (octet in texte.toByteArray(Charsets.UTF_8)) {
        val c = octet.toInt() and 0xFF
        val nonReserve = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
        if (nonReserve) append(c.toChar()) else append('%').append("%02X".format(c))
    }
}
