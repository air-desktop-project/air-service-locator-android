package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.CodeDInscription
import org.airdesktop.servicelocator.modele.DetailDuDomaine
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.EtatDeLaPaire
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.MachineDuDomaine
import org.airdesktop.servicelocator.modele.VoieDuMembre
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Ce que l'annuaire rend des domaines et des annuaires locaux (`docs/protocole.md` §2.2), lu en objets.
 *
 * # DES FONCTIONS PURES, POUR ÊTRE ÉPROUVÉES SANS ANNUAIRE
 *
 * Le transport réel ne se lance pas dans un essai JVM ; ce qu'il reçoit, si. Tout le JSON des domaines passe par ici,
 * et un essai lui donne les octets que le serveur écrit. **Une entrée illisible est sautée, pas fatale** : un champ
 * nouveau d'un annuaire plus récent ne doit pas vider l'écran.
 */
object LectureDesDomaines {
    /** `GET /v1/domaines` — un tableau. */
    fun domaines(corps: String): List<Domaine> {
        val liste = JSONArray(corps)
        return (0 until liste.length()).mapNotNull { domaine(liste.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /** `GET /v1/domaines/{d}` — le domaine, puis ses machines (vides pour qui ne le voit pas). */
    fun detail(corps: String): DetailDuDomaine? {
        val objet = JSONObject(corps)
        val domaine = domaine(objet) ?: return null
        val machines = objet.optJSONArray("machines") ?: JSONArray()
        return DetailDuDomaine(domaine, (0 until machines.length()).mapNotNull { machine(machines.optJSONObject(it) ?: return@mapNotNull null) })
    }

    /** `POST /v1/domaines` — `201 {"domaine":"d-…"}`. */
    fun domaineCree(corps: String): Identifiant? = identifiant(JSONObject(corps), "domaine", Genre.DOMAINE)

    /** `GET /v1/annuaires` et `GET /v1/inscriptions` — un tableau d'inscriptions. */
    fun inscriptions(corps: String): List<Inscription> {
        val liste = JSONArray(corps)
        return (0 until liste.length()).mapNotNull { inscription(liste.optJSONObject(it) ?: return@mapNotNull null) }
    }

    /** `POST /v1/annuaires` et `POST /v1/annuaires/{n}/membres` — `201 {"code":"XXXXX-XXXXX","expire_a":<ms>}`. */
    fun code(corps: String): CodeDInscription? {
        val objet = JSONObject(corps)
        val code = objet.optString("code").takeIf { it.isNotEmpty() } ?: return null
        if (!objet.has("expire_a")) return null
        return CodeDInscription(code, Instant.ofEpochMilli(objet.getLong("expire_a")))
    }

    private fun domaine(objet: JSONObject): Domaine? {
        val id = identifiant(objet, "domaine", Genre.DOMAINE) ?: return null
        val proprietaire = identifiant(objet, "proprietaire", Genre.UTILISATEUR) ?: return null
        val heberge = objet.optString("heberge_par", "racines")
        val droits = objet.optJSONArray("droits")?.let { d -> (0 until d.length()).map { d.getString(it) }.toSet() }.orEmpty()
        return Domaine(
            id = id,
            proprietaire = proprietaire,
            alias = texte(objet, "alias"),
            // « racines », ou un `n-…` ; un mot qu'on ne connaît pas se lit comme les racines — on n'invente pas d'hébergeur.
            hebergePar = if (heberge == "racines") null else runCatching { Identifiant.analyser(heberge, Genre.ANNUAIRE) }.getOrNull(),
            droits = droits,
            // Une chaîne, sur R seul (0.39.0). Absente, d'une autre forme, ou un mot qu'on ne connaît pas : un domaine
            // ordinaire — on ne retire un geste que sur ce qu'on sait.
            racine = objet.opt("sorte") as? String == "racine",
        )
    }

    private fun machine(objet: JSONObject): MachineDuDomaine? {
        val machine = identifiant(objet, "machine", Genre.MACHINE) ?: return null
        val proprietaire = identifiant(objet, "proprietaire", Genre.UTILISATEUR) ?: return null
        return MachineDuDomaine(machine, proprietaire, texte(objet, "nom"), texte(objet, "alias"), LectureDeLEcho.echo(objet))
    }

    private fun inscription(objet: JSONObject): Inscription? {
        val mot = objet.optString("etat")
        val adresse = objet.optString("adresse")
        if (mot.isEmpty() || adresse.isEmpty()) return null
        return Inscription(
            etat = EtatDInscription.depuisMot(mot),
            motDeLEtat = mot,
            membre = identifiant(objet, "membre", Genre.ANNUAIRE),
            annuaire = identifiant(objet, "annuaire", Genre.ANNUAIRE),
            proprietaire = identifiant(objet, "proprietaire", Genre.UTILISATEUR),
            adresse = adresse,
            expireA = if (objet.has("expire_a") && !objet.isNull("expire_a")) Instant.ofEpochMilli(objet.getLong("expire_a")) else null,
            // Deux chaînes (0.36.0 et 0.38.0) : absentes d'un annuaire plus ancien ou d'un membre qui n'a pas encore
            // parlé à cette racine. Une valeur d'une autre forme se lit comme absente — jamais un plantage.
            paire = EtatDeLaPaire.depuisMot(objet.opt("paire") as? String),
            voie = VoieDuMembre.depuisMot(objet.opt("voie") as? String),
        )
    }

    private fun identifiant(objet: JSONObject, cle: String, genre: Genre): Identifiant? =
        texte(objet, cle)?.let { runCatching { Identifiant.analyser(it, genre) }.getOrNull() }

    private fun texte(objet: JSONObject, cle: String): String? =
        if (objet.has(cle) && !objet.isNull(cle)) objet.getString(cle).takeIf { it.isNotEmpty() } else null
}
