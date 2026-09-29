package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Candidat
import org.airdesktop.servicelocator.modele.Diagnostic
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Ce que l'annuaire rend des services d'une machine (`GET /v1/machines/{m}/services`), lu en objets.
 *
 * # UNE FONCTION PURE, POUR ÊTRE ÉPROUVÉE SANS ANNUAIRE
 *
 * Même raison que [LectureDesDomaines] : le transport réel ne se lance pas dans un essai JVM, ce qu'il reçoit si.
 * **Une entrée illisible est sautée, pas fatale** ; un champ qu'on ne connaît pas est ignoré.
 *
 * Pour un service vivant, la réponse d'annonce du serveur est réémise telle quelle sous `annonce` (c'est le même objet
 * que `GET /v1/ou`, et il n'est pas aplati pour ne pas exister deux fois). Depuis le serveur 0.32.0 (décision 60), un
 * service d'une machine confiée à un annuaire local porte en plus `sonde_par` et `sonde_locale`. Depuis le 0.40.0
 * (décision 104), un service vivant d'une machine d'un AUTRE compte, lu sous le seul droit `voir`, porte un objet
 * d'annonce **vide** : il se lit [Service.sansAdresse], sans diagnostic — un diagnostic vide dirait « rien observé ».
 */
object LectureDesServices {
    /** `GET /v1/machines/{m}/services` — un tableau. */
    fun services(corps: String): List<Service> {
        val liste = JSONArray(corps)
        return (0 until liste.length()).mapNotNull { i -> service(liste.optJSONObject(i) ?: return@mapNotNull null) }
    }

    private fun service(enveloppe: JSONObject): Service? {
        val id = runCatching { Identifiant.analyser(enveloppe.getString("service"), Genre.SERVICE) }.getOrNull() ?: return null
        val nom = enveloppe.optString("nom").ifEmpty { id.abrege }
        // L'origine de la sonde : absente pour un service que la racine tient elle-même, ou chez un annuaire d'avant.
        val sondePar = enveloppe.optString("sonde_par").takeIf { it.isNotEmpty() }
            ?.let { runCatching { Identifiant.analyser(it, Genre.ANNUAIRE) }.getOrNull() }
        val sondeLocale = enveloppe.optBoolean("sonde_locale", false)
        val objet = enveloppe.optJSONObject("annonce")
        if (enveloppe.optString("etat") != "annonce" || objet == null) {
            // Parti — et le serveur ne sait plus toujours si c'était voulu.
            val volontaire = if (enveloppe.isNull("volontaire")) null else enveloppe.optBoolean("volontaire")
            return Service(id, nom, emptyList(), Service.Etat.Parti(volontaire, millis(enveloppe, "parti_a")), sondePar = sondePar, sondeLocale = sondeLocale)
        }
        if (objet.length() == 0) {
            return Service(
                id, nom, emptyList(), Service.Etat.Annonce(millis(enveloppe, "annonce_a") ?: Instant.now()),
                sondePar = sondePar, sondeLocale = sondeLocale, sansAdresse = true,
            )
        }
        val points = mutableListOf<PointEcoute>()
        val joignabilite = mutableMapOf<PointEcoute, Joignabilite>()
        val candidats = mutableListOf<Candidat>()
        val verdicts = objet.optJSONArray("joignabilite") ?: JSONArray()
        for (j in 0 until verdicts.length()) {
            val v = verdicts.optJSONObject(j) ?: continue
            val protocole = PointEcoute.Protocole.entries.firstOrNull { it.libelle == v.optString("protocole") } ?: continue
            val point = PointEcoute(protocole, v.optInt("port"))
            points += point
            joignabilite[point] = when (v.optString("verdict")) {
                "joignable" -> {
                    val candidat = v.optString("candidat")
                    adresseEtPort(candidat)?.let { (adresse, port) -> candidats += Candidat(protocole, adresse, port, Candidat.Origine.REFLEXIF) }
                    Joignabilite.Joignable(millis(v, "a") ?: Instant.now(), candidat)
                }
                "injoignable" -> Joignabilite.Injoignable(millis(v, "a") ?: Instant.now())
                "non_sonde" -> Joignabilite.NonSonde
                else -> Joignabilite.EnCours
            }
        }
        // Ce que l'annuaire a répondu au daemon, tel quel.
        val vu = objet.optJSONObject("vu_depuis")
        val diagnostic = Diagnostic(
            vuDepuis = vu?.let { v -> v.optString("adresse").let { a -> if (a.contains(':')) "[$a]:${v.optInt("port")}" else "$a:${v.optInt("port")}" } },
            derriereNat = Diagnostic.Nat.entries.firstOrNull { it.libelle == objet.optString("derriere_nat") },
            keepaliveSecondes = if (objet.has("keepalive_secondes")) objet.getInt("keepalive_secondes") else null,
            inactiviteSecondes = if (objet.has("inactivite_secondes")) objet.getInt("inactivite_secondes") else null,
        )
        return Service(
            id, nom, points, Service.Etat.Annonce(millis(enveloppe, "annonce_a") ?: Instant.now()), joignabilite, candidats,
            diagnostic = diagnostic, sondePar = sondePar, sondeLocale = sondeLocale,
        )
    }

    /** `[2001:db8::1]:49152` ou `203.0.113.4:49152`. */
    private fun adresseEtPort(texte: String): Pair<String, Int>? {
        val deuxPoints = texte.lastIndexOf(':').takeIf { it > 0 } ?: return null
        val port = texte.substring(deuxPoints + 1).toIntOrNull() ?: return null
        val adresse = texte.substring(0, deuxPoints).removePrefix("[").removeSuffix("]")
        return adresse to port
    }

    private fun millis(objet: JSONObject, cle: String): Instant? =
        if (objet.has(cle) && !objet.isNull(cle)) Instant.ofEpochMilli(objet.getLong(cle)) else null
}
