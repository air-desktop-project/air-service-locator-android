package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Echo
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.json.JSONObject
import java.time.Instant

/**
 * L'état d'écho d'une machine (serveur 0.43.0), lu sur l'objet machine de `GET /v1/machines` comme sur celui de
 * `GET /v1/domaines/{d}` — les mêmes clés aux deux endroits, donc un seul lecteur.
 *
 * # PAR CLÉS, ET SANS RIEN PRÉSUMER DU TYPE
 *
 * `protocole.md` §3 quater le promet aux annuaires futurs : les applications lisent la machine par clés et ignorent le
 * reste, **quelle qu'en soit la valeur**. Ici, un champ d'une autre forme que celle attendue (un nombre pour `echo`,
 * une chaîne pour `echo_a`) se lit comme absent, un par un : on n'emploie pas `getLong`/`getString`, qui convertissent
 * en silence ou lèvent, mais `opt` et un transtypage qui échoue en `null`.
 */
object LectureDeLEcho {
    /** `null` si l'objet ne porte pas `echo` sous forme de chaîne non vide : l'annuaire n'en dit rien. */
    fun echo(objet: JSONObject): Echo? {
        val mot = (objet.opt("echo") as? String)?.takeIf { it.isNotEmpty() } ?: return null
        return Echo(
            etat = Echo.Etat.depuisMot(mot),
            mot = mot,
            // Un entier JSON arrive en Integer ou en Long selon sa taille ; un décimal ou une chaîne n'est pas un instant.
            a = when (val a = objet.opt("echo_a")) {
                is Int -> Instant.ofEpochMilli(a.toLong())
                is Long -> Instant.ofEpochMilli(a)
                else -> null
            },
            par = (objet.opt("echo_par") as? String)?.let { runCatching { Identifiant.analyser(it, Genre.ANNUAIRE) }.getOrNull() },
            depuis = Echo.Depuis.depuisMot(objet.opt("echo_depuis") as? String),
            via = (objet.opt("echo_via") as? String)?.takeIf { it.isNotEmpty() },
        )
    }
}
