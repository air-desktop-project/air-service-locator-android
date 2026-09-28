package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Une racine désignée par son **identité** (spec serveur #54, décisions 53–58) :
 * le `n-…` qu'on doit trouver au bout de la connexion — la clé de son
 * certificat auto-signé s'y déduit —, et ses **locateurs**, des adresses
 * LITTÉRALES (`[IPv6]:port` ou `IPv4:port`). Aucun nom : C20, « ASL fonctionne
 * sans DNS ».
 */
data class RacineIdentifiee(val annuaire: String, val locateurs: List<String>)

/**
 * Une racine de l'annuaire, telle que la configuration la décrit.
 *
 * **On n'y parle que par l'identité** : [identites], une ou plusieurs racines
 * jointes par leurs locateurs littéraux et crues par leur clé — aucun nom à
 * résoudre, aucune autorité (fin de la transition, décision 58 ; C20).
 * `adresse` et `nom` ne servent plus à se connecter : `adresse` reste la clé de
 * la préférence retenue (un choix d'hier le reste), `nom` ce que l'écran montre
 * à défaut de `libelle`.
 */
data class RacineDAnnuaire(
    val adresse: String,
    val nom: String,
    val libelle: String? = null,
    val identites: List<RacineIdentifiee> = emptyList(),
) {
    /** Ce que l'écran montre : le libellé s'il y en a un, sinon le nom de la racine. */
    val affichee: String get() = libelle ?: nom

    /** Jointe par l'identité : aucun nom n'est résolu pour elle. */
    val parIdentite: Boolean get() = identites.isNotEmpty()

    /**
     * Ce que la préférence retient : l'adresse, **la même clé que les versions
     * d'avant** (un choix retenu hier le reste) ; sinon les `n-…` joints, pour
     * une entrée qui n'a plus d'adresse. La règle de l'application iOS.
     */
    val cle: String get() = adresse.ifEmpty { identites.joinToString(",") { it.annuaire } }

    /**
     * Ce que le journal et la commande d'inscription disent de la cible : le premier locateur et l'identité
     * attendue (`locateur=n-…`, la forme que `asl-server --directory` accepte) — jamais un nom.
     */
    val affichePourLeJournal: String
        get() = identites.firstOrNull()?.let { r -> r.locateurs.firstOrNull()?.let { "$it=${r.annuaire}" } } ?: nom
}

/**
 * Les racines qu'on sait nommer d'après leur identité — pour dire « Connecté à
 * … » sans rien résoudre. Un `n-…` inconnu se dit abrégé. La même table que
 * l'application iOS (`RacinesConnues`).
 */
object RacinesConnues {
    val noms: Map<String, String> = mapOf(
        "n-0PWT8HZD80QMSPPDZ5CQXXYHQC" to "nitrogen.air-desktop.org",
        "n-3K3P6H252W8K9370QG1YYTWBWB" to "argon.air-desktop.org",
    )

    fun nom(annuaire: String): String =
        noms[annuaire] ?: runCatching { Identifiant.analyser(annuaire, Genre.ANNUAIRE).abrege }.getOrDefault(annuaire)
}

/**
 * La liste des racines, lue dans la forme que l'application iOS lit aussi
 * (`annuaire.json`, dépôt `-ios`, PR #29) — chaque entrée porte l'identité de
 * sa ou ses racines :
 *
 * ```json
 * {"annuaires": [
 *   {"libelle": "Automatique", "adresse": "asl-root.air-desktop.org:6630", "nom": "asl-root.air-desktop.org",
 *    "racines": [
 *      {"annuaire": "n-0PWT8HZD80QMSPPDZ5CQXXYHQC", "locateurs": ["[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"]},
 *      {"annuaire": "n-3K3P6H252W8K9370QG1YYTWBWB", "locateurs": ["[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630"]}]},
 *   {"adresse": "nitrogen.air-desktop.org:6630", "nom": "nitrogen.air-desktop.org",
 *    "annuaire": "n-0PWT8HZD80QMSPPDZ5CQXXYHQC", "locateurs": ["[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"]}
 * ]}
 * ```
 *
 * L'écriture courte (`annuaire` + `locateurs`) et la longue (`racines`, pour
 * une entrée qui en couvre plusieurs) se cumulent, la courte en tête.
 * `adresse`/`nom` sont gardés dans le fichier (les versions d'avant ne lisent
 * qu'eux), mais **une entrée sans identité n'est plus utilisable** : depuis
 * que les racines ne présentent que leur certificat d'identité, rien ne
 * permettrait de la croire. Elle est laissée de côté, et dite au journal.
 */
object ListeDAnnuaires {
    /**
     * Les racines décrites, dans leur ordre. **Deux entrées de même [clé]
     * [RacineDAnnuaire.cle] n'en font qu'une** (la première).
     *
     * Un `n-…` de travers ou un locateur qui n'est pas une adresse littérale
     * est laissé de côté — jamais résolu. Une entrée sans identité (la forme
     * d'hier, adresse et nom seuls) est sautée et [journal] le dit. Un texte
     * illisible rend une liste VIDE.
     */
    fun lire(json: String, journal: (String) -> Unit = {}): List<RacineDAnnuaire> {
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
                val identites = identites(entree)
                val adresse = entree.optString("adresse")
                val nom = entree.optString("nom")
                if (identites.isEmpty()) {
                    journal("entrée « ${adresse.ifEmpty { nom.ifEmpty { "sans nom" } }} » laissée de côté : aucune identité (`annuaire` n-… et locateurs littéraux) — les racines ne se croient plus que par leur clé")
                    return@mapNotNull null
                }
                RacineDAnnuaire(
                    adresse = adresse,
                    nom = nom.ifEmpty { RacinesConnues.nom(identites.first().annuaire) },
                    libelle = entree.optString("libelle").takeIf { it.isNotEmpty() },
                    identites = identites,
                )
            }
            .distinctBy { it.cle }
    }

    /** Les racines identifiées d'une entrée : l'écriture courte d'abord, puis `racines`. */
    private fun identites(entree: JSONObject): List<RacineIdentifiee> {
        val brutes = mutableListOf<Pair<String, JSONArray?>>()
        entree.optString("annuaire").takeIf { it.isNotEmpty() }?.let { brutes += it to entree.optJSONArray("locateurs") }
        entree.optJSONArray("racines")?.let { liste ->
            for (i in 0 until liste.length()) {
                val r = liste.optJSONObject(i) ?: continue
                brutes += r.optString("annuaire") to r.optJSONArray("locateurs")
            }
        }
        return brutes.mapNotNull { (annuaire, locateurs) ->
            if (runCatching { Identifiant.analyser(annuaire, Genre.ANNUAIRE) }.isFailure) return@mapNotNull null
            val litteraux = (0 until (locateurs?.length() ?: 0)).mapNotNull { locateurs?.optString(it) }.filter(::estLitteral)
            litteraux.takeIf { it.isNotEmpty() }?.let { RacineIdentifiee(annuaire, it) }
        }
    }

    /**
     * `[IPv6]:port` ou `IPv4:port`, et rien d'autre : un nom n'est PAS un
     * locateur (C20). Jugé sans résolveur — une chaîne qui n'a que des chiffres
     * hexadécimaux et des `:` ne peut pas déclencher de requête DNS.
     */
    fun estLitteral(locateur: String): Boolean {
        val deuxPoints = locateur.lastIndexOf(':').takeIf { it > 0 } ?: return false
        val port = locateur.substring(deuxPoints + 1).toIntOrNull() ?: return false
        if (port !in 1..65535) return false
        val hote = locateur.substring(0, deuxPoints)
        if (hote.startsWith("[") && hote.endsWith("]")) {
            val v6 = hote.substring(1, hote.length - 1)
            if (v6.isEmpty() || !v6.contains(':') || !v6.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) return false
            return runCatching { java.net.InetAddress.getByName(v6) is java.net.Inet6Address }.getOrDefault(false)
        }
        val octets = hote.split('.')
        return octets.size == 4 && octets.all { o -> o.isNotEmpty() && o.length <= 3 && o.all(Char::isDigit) && o.toInt() in 0..255 }
    }
}

/**
 * Où le choix se retient. **Par application, pas par compte** : le compte
 * existe sur chaque racine, puisqu'elles se répliquent. Sur un téléphone, ce
 * sont des préférences partagées (clé `annuaire.adresse`, la même que
 * l'application iOS) ; dans un essai, une variable.
 */
interface MemoireDuChoix {
    /** La [clé][RacineDAnnuaire.cle] de la racine retenue, ou `null` si rien ne l'a encore été. */
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
    var choisie: RacineDAnnuaire = racines.firstOrNull { it.cle == memoire.adresse } ?: racines.first()
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
        require(racines.any { it.cle == racine.cle }) { "« ${racine.cle} » n'est pas dans la liste" }
        if (racine.cle == choisie.cle) return false
        annuaire.fermer()
        annuaire = fabrique(racine)
        choisie = racine
        memoire.adresse = racine.cle
        return true
    }
}
