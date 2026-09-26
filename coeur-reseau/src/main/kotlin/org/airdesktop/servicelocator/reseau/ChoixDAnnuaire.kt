package org.airdesktop.servicelocator.reseau

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Une racine de l'annuaire, telle que la configuration la décrit.
 *
 * `adresse` est `hôte:port` (un nom se résout au moment de se connecter, et
 * TOUTES ses adresses sont posées — c'est ce qui fait marcher un alias qui
 * désigne plusieurs racines) ; `nom` est celui qu'on EXIGE du certificat ;
 * `libelle`, facultatif, est ce que l'écran montre à la place du nom.
 */
data class RacineDAnnuaire(val adresse: String, val nom: String, val libelle: String? = null) {
    /** Ce que l'écran montre : le libellé s'il y en a un, sinon le nom du certificat. */
    val affichee: String get() = libelle ?: nom
}

/**
 * La liste des racines, lue dans la forme que l'application iOS lit aussi
 * (`annuaire.json`, dépôt `-ios`, PR #22) :
 *
 * ```json
 * {"annuaires": [
 *   {"adresse": "nitrogen.air-desktop.org:6630", "nom": "nitrogen.air-desktop.org"},
 *   {"adresse": "argon.air-desktop.org:6630", "nom": "argon.air-desktop.org"},
 *   {"adresse": "asl-root.air-desktop.org:6630", "nom": "asl-root.air-desktop.org", "libelle": "Automatique"}
 * ]}
 * ```
 *
 * **L'ancienne forme reste lue** — un objet seul, `{"adresse", "nom"}` —
 * comme une liste d'un élément : un fichier d'avant la liste ne casse rien.
 * Une seule racine PEM vaut pour toutes (elle vient d'ailleurs).
 */
object ListeDAnnuaires {
    /**
     * Les racines décrites, dans leur ordre. **Deux entrées à la même adresse
     * n'en font qu'une** (la première) : le choix est retenu par l'adresse, et
     * deux lignes qui la partagent seraient indiscernables une fois retenues.
     *
     * Une entrée sans adresse ou sans nom est sautée ; un texte illisible rend
     * une liste VIDE — l'application tourne alors sur le banc en mémoire,
     * comme sans configuration, plutôt que sur une racine devinée.
     */
    fun lire(json: String): List<RacineDAnnuaire> {
        val racine = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return emptyList()
        }
        val entrees: List<JSONObject> = when (val liste = racine.opt("annuaires")) {
            is JSONArray -> (0 until liste.length()).mapNotNull { liste.optJSONObject(it) }
            null -> listOf(racine)
            else -> emptyList()
        }
        return entrees
            .mapNotNull { entree ->
                val adresse = entree.optString("adresse").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val nom = entree.optString("nom").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RacineDAnnuaire(adresse, nom, entree.optString("libelle").takeIf { it.isNotEmpty() })
            }
            .distinctBy { it.adresse }
    }
}

/**
 * Où le choix se retient. **Par application, pas par compte** : le compte
 * existe sur chaque racine, puisqu'elles se répliquent. Sur un téléphone, ce
 * sont des préférences partagées (clé `annuaire.adresse`, la même que
 * l'application iOS) ; dans un essai, une variable.
 */
interface MemoireDuChoix {
    /** L'adresse de la racine retenue, ou `null` si rien ne l'a encore été. */
    var adresse: String?
}

/**
 * La racine à qui l'application parle, et le passage de l'une à l'autre.
 *
 * **Par défaut, la première de la liste** ; une préférence qui nomme une
 * racine retirée depuis de la configuration y retombe aussi — la liste fait
 * foi, le souvenir ne fait que choisir dedans.
 *
 * # CE QUE CHANGER DE RACINE CHANGE, ET CE QUI NE BOUGE PAS
 *
 * L'annuaire en cours est [fermé][Annuaire.fermer] ; le suivant est fabriqué
 * SANS se connecter. C'est la première demande qui suit qui ouvre la connexion,
 * et la preuve de la clé y demande l'empreinte, comme à chaque connexion :
 * **jamais de reconnexion silencieuse**. La clé de l'appareil, le carnet local
 * et le compte ne bougent pas — ils sont les mêmes sur chaque racine.
 *
 * Générique en [A] pour que l'application garde le type de ce qu'elle fabrique
 * (l'annuaire réel ouvre un compte d'une façon que l'interface ne dit pas).
 */
class ChoixDAnnuaire<A : Annuaire>(
    val racines: List<RacineDAnnuaire>,
    private val memoire: MemoireDuChoix,
    private val fabrique: (RacineDAnnuaire) -> A,
) {
    init {
        require(racines.isNotEmpty()) { "une liste de racines vide : rien à choisir" }
    }

    /** La racine en service. */
    var choisie: RacineDAnnuaire = racines.firstOrNull { it.adresse == memoire.adresse } ?: racines.first()
        private set

    /** L'annuaire de [choisie]. */
    var annuaire: A = fabrique(choisie)
        private set

    /** Y a-t-il un choix à montrer ? Une seule racine n'en offre pas. */
    val offreUnChoix: Boolean get() = racines.size > 1

    /**
     * Passe à [racine]. Rend `false` sans rien toucher si c'est déjà elle —
     * rechoisir la racine en service ne ferme pas sa connexion.
     *
     * L'ordre compte : fermer l'ancien AVANT d'en fabriquer un autre, pour
     * qu'aucune requête ne parte encore sur une connexion qu'on quitte ; puis
     * retenir, une fois le nouveau en place.
     */
    suspend fun choisir(racine: RacineDAnnuaire): Boolean {
        require(racines.any { it.adresse == racine.adresse }) { "« ${racine.adresse} » n'est pas dans la liste" }
        if (racine.adresse == choisie.adresse) return false
        annuaire.fermer()
        annuaire = fabrique(racine)
        choisie = racine
        memoire.adresse = racine.adresse
        return true
    }
}
