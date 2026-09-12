package org.airdesktop.servicelocator.reseau

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Candidat
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.NonConfirmeException
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.modele.Signataire
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * L'annuaire, par le transport réel : la pile QUIC d'`asl-client`, derrière
 * ses symboles JNI (`asl-client-android`).
 *
 * # Ce que fait le natif, et ce qui reste ici
 *
 * Le natif ouvre la connexion, exporte la liaison de canal, tire le défi,
 * porte la preuve et tient la connexion vivante. **Il ne signe rien** : quand
 * il a besoin d'une signature, il rappelle le [Natif.Signataire] posé ici, sur
 * le fil qui a fait l'appel — et c'est là que le Keystore demande l'empreinte.
 *
 * Ce qui reste ici est ce qu'un téléphone fait mieux qu'une bibliothèque :
 * composer et lire du JSON, et décider quoi montrer d'un `404`.
 *
 * # Un seul fil, et jamais le principal
 *
 * Les appels sur un handle ne se chevauchent pas, et un rappel de signature
 * bloque le fil qui l'a provoqué le temps du geste. Tout passe donc par un
 * verrou, sur `Dispatchers.IO`.
 *
 * # Ce que le serveur ne sert pas encore, et comment on l'attend
 *
 * `GET /v1/machines` et `GET /v1/appareils` n'existent pas encore côté serveur
 * (voir `CLAUDE.md` du dépôt client). Les machines que CET appareil a
 * déclarées sont donc retenues localement ([Carnet]) : un second appareil du
 * même compte ne les verrait pas. Le jour où le verbe existe, [machines] le
 * préfère, et le carnet n'est plus qu'un cache.
 */
class AnnuaireReel(
    contexte: Context,
    private val reglages: Reglages,
    private val signataire: () -> Signataire,
) : Annuaire {
    /** Où est l'annuaire, sous quel nom, et qui a signé son certificat. */
    data class Reglages(val adresse: String, val nom: String, val racinesPEM: ByteArray)

    class ErreurNative(val code: Int) : Exception("natif : ${Natif.fauteTexte(code)} ($code)")

    private val carnet = Carnet(contexte)
    private val verrou = Mutex()
    private var handle = 0L
    private var cleCourante: Signataire? = null

    // ── Le natif ──────────────────────────────────────────────────────────────

    private suspend fun <T> surLeFil(travail: () -> T): T = verrou.withLock { withContext(Dispatchers.IO) { travail() } }

    private fun exiger(code: Int, quoi: String = "natif") {
        Log.d("annuaire", "$quoi → $code")
        if (code != Natif.OK) throw ErreurNative(code)
    }

    /** Le handle, créé et réglé à la première demande. */
    private fun handleOuCreer(): Long {
        if (handle != 0L) return handle
        val neuf = Natif.neuf()
        Log.d("annuaire", "neuf → $neuf")
        if (neuf == 0L) {
            Log.e("annuaire", Natif.diagnostic())
            throw ErreurNative(Natif.INTERNE)
        }
        exiger(Natif.annuaire(neuf, reglages.adresse, reglages.nom), "annuaire")
        exiger(Natif.racines(neuf, reglages.racinesPEM), "racines")
        val cle = signataire()
        cleCourante = cle
        // Le rappel : le natif est sur notre fil (le verrou l'assure), et la
        // clé attend le porteur. On bloque ce fil le temps qu'elle signe.
        exiger(Natif.cle(neuf, cle.clePublique) { message ->
            Log.d("annuaire", "rappel de signature : ${message.size} octets")
            runCatching { runBlocking { cle.signer(message) } }
                .onFailure { Log.e("annuaire", "la clé n'a pas signé", it) }
                .getOrNull()
        }, "cle")
        carnet.appareil?.let { exiger(Natif.identite(neuf, it.texte), "identite") }
        handle = neuf
        return neuf
    }

    /** Ouvre la connexion — et prouve la clé si l'appareil est enrôlé. C'est ici que l'empreinte est demandée, une fois par connexion. */
    private fun connecter() {
        val h = handleOuCreer()
        Log.d("annuaire", "connexion à ${reglages.adresse}…")
        when (val code = Natif.connecter(h).also { Log.d("annuaire", "connecter → $it") }) {
            Natif.OK -> Unit
            Natif.SIGNATURE_REFUSEE -> throw ErreurAnnuaire.NonConfirme
            Natif.REFUSE -> throw ErreurAnnuaire.PreuveInvalide
            Natif.INJOIGNABLE -> throw ErreurAnnuaire.Reseau("aucun annuaire ne répond")
            else -> throw ErreurNative(code)
        }
    }

    private fun connecte(): Boolean {
        if (handle == 0L) return false
        // Une requête à vide dit si la tenue vit : `NON_CONNECTE` sinon.
        return Natif.requete(handle, "GET", "/v1/vu", null) != null || Natif.dernierCode(handle) != Natif.NON_CONNECTE
    }

    /** Une requête de `protocole.md` §2 : méthode, chemin, corps JSON. */
    private fun requete(methode: String, chemin: String, corps: String? = null): Pair<Int, String> {
        val h = handleOuCreer()
        if (!connecte()) connecter()
        val rendu = Natif.requete(h, methode, chemin, corps?.toByteArray())
            ?: when (val code = Natif.dernierCode(h)) {
                Natif.INJOIGNABLE, Natif.NON_CONNECTE -> throw ErreurAnnuaire.Reseau("la connexion est tombée")
                else -> throw ErreurNative(code)
            }
        val statut = ((rendu[0].toInt() and 0xFF) shl 8) or (rendu[1].toInt() and 0xFF)
        // Le chemin et le statut, jamais le corps : un identifiant de compte
        // n'a rien à faire dans Logcat.
        Log.d("annuaire", "$methode $chemin → $statut (${rendu.size - 2} octets)")
        return statut to String(rendu, 2, rendu.size - 2, Charsets.UTF_8)
    }

    private fun refus(statut: Int): ErreurAnnuaire = when (statut) {
        404 -> ErreurAnnuaire.Introuvable
        403 -> ErreurAnnuaire.Interdit
        409 -> ErreurAnnuaire.AliasPris
        401 -> ErreurAnnuaire.NonConfirme
        501 -> ErreurAnnuaire.NonImplemente
        else -> ErreurAnnuaire.RequeteInvalide("l'annuaire a répondu $statut")
    }

    private fun millis(objet: JSONObject, cle: String): Instant? =
        if (objet.has(cle) && !objet.isNull(cle)) Instant.ofEpochMilli(objet.getLong(cle)) else null

    // ── Compte ────────────────────────────────────────────────────────────────

    override suspend fun ouvrirCompte(signataire: Signataire): Compte = surLeFil {
        carnet.compte?.let { return@surLeFil it }
        val h = handleOuCreer()
        if (!connecte()) connecter()
        val rendu = Natif.creerCompte(h, Natif.PLATEFORME_AUCUNE, null)
            ?: when (val code = Natif.dernierCode(h)) {
                Natif.SIGNATURE_REFUSEE -> throw ErreurAnnuaire.NonConfirme
                Natif.REFUSE -> throw ErreurAnnuaire.PreuveInvalide
                else -> throw ErreurNative(code)
            }
        val compte = Compte(Identifiant.analyser(rendu[0], Genre.UTILISATEUR))
        carnet.compte = compte
        carnet.appareil = Identifiant.analyser(rendu[1], Genre.APPAREIL)
        compte
    }

    override suspend fun compte(): Compte? {
        val compte = carnet.compte ?: return null
        // Se connecter au lancement, c'est prouver la clé : l'empreinte, une fois.
        val (statut, corps) = surLeFil {
            if (!connecte()) connecter()
            requete("GET", "/v1/utilisateurs/${compte.identifiant.texte}")
        }
        if (statut != 200) return compte
        val objet = JSONObject(corps)
        val relu = compte.copy(alias = if (objet.has("alias")) objet.getString("alias") else null)
        carnet.compte = relu
        return relu
    }

    override suspend fun definirAlias(alias: String?) {
        val (statut, _) = surLeFil {
            if (alias != null) requete("PUT", "/v1/alias", JSONObject().put("alias", alias).toString())
            else requete("DELETE", "/v1/alias")
        }
        if (statut != 204) throw refus(statut)
        carnet.compte = carnet.compte?.copy(alias = alias)
    }

    // ── Machines ──────────────────────────────────────────────────────────────

    override suspend fun machines(): List<Machine> {
        val (statut, corps) = surLeFil { requete("GET", "/v1/machines") }
        val machines = if (statut == 200) {
            val liste = JSONArray(corps)
            (0 until liste.length()).mapNotNull { machine(liste.getJSONObject(it)) }.also { carnet.machines = it }
        } else {
            // Le verbe n'existe pas encore : ce que cet appareil a déclaré.
            carnet.machines
        }
        return machines.map { it.copy(services = runCatching { services(it.id) }.getOrDefault(emptyList())) }
    }

    private fun machine(objet: JSONObject): Machine? {
        val id = runCatching { Identifiant.analyser(objet.getString("machine"), Genre.MACHINE) }.getOrNull() ?: return null
        val capacites = objet.optJSONArray("capacites")?.let { c -> (0 until c.length()).mapNotNull { i -> Capacite.entries.firstOrNull { it.libelle == c.getString(i) } } }.orEmpty().toSet()
        val cle = when (objet.optString("cle")) {
            "enrolee" -> Machine.Cle.Enrolee(millis(objet, "enrolee_a") ?: Instant.now())
            "revoquee" -> Machine.Cle.Revoquee(millis(objet, "revoquee_a") ?: Instant.now(), code(objet))
            else -> Machine.Cle.Attendue(code(objet) ?: CodeEnrolement("0000000000", Instant.EPOCH))
        }
        return Machine(id, objet.getString("nom"), capacites, cle)
    }

    /** Le code que l'annuaire rend : dix symboles, groupés ou non, et sa date. */
    private fun code(objet: JSONObject): CodeEnrolement? {
        val texte = objet.optString("code").replace("-", "").uppercase()
        val expire = millis(objet, "expire_a") ?: return null
        if (texte.length != CodeEnrolement.NOMBRE_SYMBOLES) return null
        return CodeEnrolement(texte, expire)
    }

    override suspend fun declarerMachine(nom: String, capacites: Set<Capacite>): Machine {
        val corps = JSONObject().put("nom", nom).put("capacites", JSONArray(Capacite.entries.filter { it in capacites }.map { it.libelle }))
        val (statut, rendu) = surLeFil { requete("POST", "/v1/machines", corps.toString()) }
        if (statut != 201) throw refus(statut)
        val objet = JSONObject(rendu)
        val machine = Machine(
            Identifiant.analyser(objet.getString("machine"), Genre.MACHINE), nom, capacites,
            Machine.Cle.Attendue(code(objet) ?: throw ErreurAnnuaire.RequeteInvalide("code absent")),
        )
        carnet.machines = carnet.machines + machine
        return machine
    }

    override suspend fun modifierMachine(id: Identifiant, nom: String?, capacites: Set<Capacite>?): Machine {
        if (nom == null && capacites == null) throw ErreurAnnuaire.RequeteInvalide("{}")
        val corps = JSONObject()
        nom?.let { corps.put("nom", it) }
        capacites?.let { c -> corps.put("capacites", JSONArray(Capacite.entries.filter { it in c }.map { it.libelle })) }
        val (statut, _) = surLeFil { requete("PATCH", "/v1/machines/${id.texte}", corps.toString()) }
        if (statut != 204) throw refus(statut)
        val actuelle = carnet.machines.firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable
        val modifiee = actuelle.copy(nom = nom ?: actuelle.nom, capacites = capacites ?: actuelle.capacites)
        carnet.remplacer(modifiee)
        return modifiee
    }

    override suspend fun emettreCode(machine: Identifiant): CodeEnrolement {
        val (statut, rendu) = surLeFil { requete("POST", "/v1/machines/${machine.texte}/enrolement") }
        if (statut != 201) throw refus(statut)
        val code = code(JSONObject(rendu)) ?: throw ErreurAnnuaire.RequeteInvalide("code absent")
        carnet.machines.firstOrNull { it.id == machine }?.let { actuelle ->
            val cle = when (val c = actuelle.cle) {
                is Machine.Cle.Revoquee -> c.copy(code = code)
                else -> Machine.Cle.Attendue(code)
            }
            carnet.remplacer(actuelle.copy(cle = cle))
        }
        return code
    }

    override suspend fun revoquerCle(machine: Identifiant) {
        val (statut, _) = surLeFil { requete("DELETE", "/v1/machines/${machine.texte}/cle") }
        if (statut != 204) throw refus(statut)
        carnet.machines.firstOrNull { it.id == machine }?.let { carnet.remplacer(it.copy(cle = Machine.Cle.Revoquee(Instant.now()))) }
    }

    /** `GET /v1/machines/{m}/services` — ce que l'annuaire en rend aujourd'hui : les réponses d'annonce des services vivants, sans leur nom. */
    private suspend fun services(machine: Identifiant): List<Service> {
        val (statut, corps) = surLeFil { requete("GET", "/v1/machines/${machine.texte}/services") }
        if (statut != 200) return emptyList()
        val liste = JSONArray(corps)
        return (0 until liste.length()).mapNotNull { i ->
            val objet = liste.getJSONObject(i)
            val id = runCatching { Identifiant.analyser(objet.getString("service"), Genre.SERVICE) }.getOrNull() ?: return@mapNotNull null
            val points = mutableListOf<PointEcoute>()
            val joignabilite = mutableMapOf<PointEcoute, Joignabilite>()
            val candidats = mutableListOf<Candidat>()
            val verdicts = objet.optJSONArray("joignabilite") ?: JSONArray()
            for (j in 0 until verdicts.length()) {
                val v = verdicts.getJSONObject(j)
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
            // Le nom manque : l'annuaire ne le rend pas encore. L'identifiant abrégé tient sa place, et l'écran ne ment pas.
            Service(id, id.abrege, points, Service.Etat.Annonce(Instant.now()), joignabilite, candidats)
        }
    }

    /** `[2001:db8::1]:49152` ou `203.0.113.4:49152`. */
    private fun adresseEtPort(texte: String): Pair<String, Int>? {
        val deuxPoints = texte.lastIndexOf(':').takeIf { it > 0 } ?: return null
        val port = texte.substring(deuxPoints + 1).toIntOrNull() ?: return null
        val adresse = texte.substring(0, deuxPoints).removePrefix("[").removeSuffix("]")
        return adresse to port
    }

    // ── Appareils ─────────────────────────────────────────────────────────────

    override suspend fun appareils(): List<Appareil> {
        val (statut, corps) = surLeFil { requete("GET", "/v1/appareils") }
        if (statut == 200) {
            val liste = JSONArray(corps)
            return (0 until liste.length()).mapNotNull { i ->
                val objet = liste.getJSONObject(i)
                val id = runCatching { Identifiant.analyser(objet.getString("appareil"), Genre.APPAREIL) }.getOrNull() ?: return@mapNotNull null
                Appareil(id, id.abrege, Appareil.Biometrie.EMPREINTE, millis(objet, "enrole_a") ?: Instant.now(), millis(objet, "revoque_a"), id == carnet.appareil)
            }
        }
        // Le verbe n'existe pas encore : cet appareil, et lui seul.
        val moi = carnet.appareil ?: return emptyList()
        return listOf(Appareil(moi, "Cet appareil", Appareil.Biometrie.EMPREINTE, carnet.enroleLe ?: Instant.now(), estCeluiCi = true))
    }

    override suspend fun revoquerAppareil(id: Identifiant) {
        val (statut, _) = surLeFil { requete("DELETE", "/v1/appareils/${id.texte}") }
        if (statut != 204) throw refus(statut)
    }

    // ── Autorisations ─────────────────────────────────────────────────────────

    override suspend fun autorisations(): List<Autorisation> {
        val (statut, corps) = surLeFil { requete("GET", "/v1/autorisations") }
        if (statut != 200) throw refus(statut)
        val liste = JSONArray(corps)
        return (0 until liste.length()).mapNotNull { autorisation(liste.getJSONObject(it)) }
    }

    private fun autorisation(objet: JSONObject): Autorisation? = runCatching {
        val portee = objet.getString("portee").let { texte ->
            if (texte == "tout") Autorisation.Portee.Tout
            else Identifiant.analyser(texte).let { if (it.genre == Genre.SERVICE) Autorisation.Portee.Service(it) else Autorisation.Portee.Machine(it) }
        }
        val revoquee = objet.optBoolean("revoquee", false)
        Autorisation(
            Identifiant.analyser(objet.getString("autorisation"), Genre.AUTORISATION),
            Identifiant.analyser(objet.getString("par"), Genre.UTILISATEUR),
            Identifiant.analyser(objet.getString("a"), Genre.UTILISATEUR),
            portee, objet.optString("etiquette"), millis(objet, "accordee_a") ?: Instant.now(),
            if (revoquee) millis(objet, "revoquee_a") ?: Instant.now() else null,
        )
    }.getOrNull()

    override suspend fun utilisateurExiste(id: Identifiant): Boolean =
        surLeFil { requete("GET", "/v1/utilisateurs/${id.texte}") }.first == 200

    override suspend fun identifiantPourAlias(alias: String): Identifiant? {
        val (statut, corps) = surLeFil { requete("GET", "/v1/alias/$alias") }
        if (statut != 200) return null
        return Identifiant.analyser(JSONObject(corps).getString("identifiant"), Genre.UTILISATEUR)
    }

    override suspend fun accorder(beneficiaire: Identifiant, portee: Autorisation.Portee, etiquette: String): Autorisation {
        val porteeTexte = when (portee) {
            Autorisation.Portee.Tout -> "tout"
            is Autorisation.Portee.Machine -> portee.id.texte
            is Autorisation.Portee.Service -> portee.id.texte
        }
        // `etiquette` n'est pas encore un champ du serveur ; elle ne part pas.
        val corps = JSONObject().put("a", beneficiaire.texte).put("portee", porteeTexte)
        val (statut, rendu) = surLeFil { requete("POST", "/v1/autorisations", corps.toString()) }
        if (statut != 201) throw refus(statut)
        val compte = carnet.compte ?: throw ErreurAnnuaire.Introuvable
        return Autorisation(
            Identifiant.analyser(JSONObject(rendu).getString("autorisation"), Genre.AUTORISATION),
            compte.identifiant, beneficiaire, portee, etiquette, Instant.now(),
        )
    }

    override suspend fun revoquerAutorisation(id: Identifiant) {
        val (statut, _) = surLeFil { requete("DELETE", "/v1/autorisations/${id.texte}") }
        if (statut != 204) throw refus(statut)
    }
}

/**
 * Ce que cet appareil retient de lui-même, et ce que le serveur ne sait pas
 * encore rendre : le compte, l'appareil enrôlé, les machines déclarées d'ici.
 *
 * `SharedPreferences` suffit : rien de secret n'y est — la clé vit dans le
 * Keystore —, et `allowBackup="false"` fait que rien ne se restaure ailleurs.
 */
class Carnet(contexte: Context) {
    private val prefs = contexte.getSharedPreferences("carnet", Context.MODE_PRIVATE)

    var compte: Compte?
        get() = prefs.getString("compte", null)?.let { runCatching { Identifiant.analyser(it, Genre.UTILISATEUR) }.getOrNull() }
            ?.let { Compte(it, prefs.getString("alias", null)) }
        set(valeur) = prefs.edit().putString("compte", valeur?.identifiant?.texte).putString("alias", valeur?.alias).apply()

    var appareil: Identifiant?
        get() = prefs.getString("appareil", null)?.let { runCatching { Identifiant.analyser(it, Genre.APPAREIL) }.getOrNull() }
        set(valeur) {
            val edition = prefs.edit().putString("appareil", valeur?.texte)
            if (valeur != null && !prefs.contains("enrole_le")) edition.putLong("enrole_le", Instant.now().toEpochMilli())
            edition.apply()
        }

    val enroleLe: Instant? get() = if (prefs.contains("enrole_le")) Instant.ofEpochMilli(prefs.getLong("enrole_le", 0)) else null

    var machines: List<Machine>
        get() = prefs.getString("machines", null)?.let { texte ->
            val liste = JSONArray(texte)
            (0 until liste.length()).mapNotNull { fiche(liste.getJSONObject(it)) }
        }.orEmpty()
        set(valeur) = prefs.edit().putString("machines", JSONArray(valeur.map(::fiche)).toString()).apply()

    fun remplacer(machine: Machine) {
        machines = machines.filter { it.id != machine.id } + machine
    }

    private fun fiche(machine: Machine): JSONObject = JSONObject().apply {
        put("id", machine.id.texte)
        put("nom", machine.nom)
        put("capacites", JSONArray(machine.capacites.map { it.libelle }))
        when (val cle = machine.cle) {
            is Machine.Cle.Attendue -> { put("cle", "attendue"); put("code", cle.code.symboles); put("expire_le", cle.code.expireLe.toEpochMilli()) }
            is Machine.Cle.Enrolee -> { put("cle", "enrolee"); put("enrolee_le", cle.le.toEpochMilli()) }
            is Machine.Cle.Revoquee -> {
                put("cle", "revoquee"); put("revoquee_le", cle.le.toEpochMilli())
                cle.code?.let { put("code", it.symboles); put("expire_le", it.expireLe.toEpochMilli()) }
            }
        }
    }

    private fun fiche(objet: JSONObject): Machine? {
        val id = runCatching { Identifiant.analyser(objet.getString("id"), Genre.MACHINE) }.getOrNull() ?: return null
        val capacites = objet.getJSONArray("capacites").let { c -> (0 until c.length()).mapNotNull { i -> Capacite.entries.firstOrNull { it.libelle == c.getString(i) } } }.toSet()
        val code = if (objet.has("code")) CodeEnrolement(objet.getString("code"), Instant.ofEpochMilli(objet.getLong("expire_le"))) else null
        val cle = when (objet.getString("cle")) {
            "enrolee" -> Machine.Cle.Enrolee(Instant.ofEpochMilli(objet.getLong("enrolee_le")))
            "revoquee" -> Machine.Cle.Revoquee(Instant.ofEpochMilli(objet.getLong("revoquee_le")), code)
            else -> Machine.Cle.Attendue(code ?: CodeEnrolement("0000000000", Instant.EPOCH))
        }
        return Machine(id, objet.getString("nom"), capacites, cle)
    }
}
