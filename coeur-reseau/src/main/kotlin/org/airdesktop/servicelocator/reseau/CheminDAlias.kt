package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Alias

/**
 * Le chemin qui résout cet alias de compte.
 *
 * # DEUX FORMES, POUR DEUX ANNUAIRES
 *
 * Jusqu'à 0.25.0, l'alias était en minuscules ASCII, dans le chemin (`/v1/alias/{alias}`). Depuis 0.26.0 il est
 * UTF-8 et sensible à la casse, et voyage pourcent-encodé en paramètre (`/v1/alias?alias=…`). Un alias en
 * minuscules ASCII prend le chemin, que les deux versions servent ; tout autre alias prend le paramètre, qu'une
 * 0.25.0 ne connaît pas — elle répondra qu'elle ne le trouve pas, ce qui est vrai : elle ne pouvait pas le tenir.
 */
fun cheminDAlias(alias: String): String {
    val forme = Alias.nfc(alias)
    return if (forme.isNotEmpty() && forme.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) "/v1/alias/$forme"
    else "/v1/alias?alias=${pourcentEncoder(forme)}"
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
