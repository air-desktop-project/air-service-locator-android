package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.Messages
import org.airdesktop.servicelocator.modele.P256
import org.airdesktop.servicelocator.modele.Service
import java.security.SecureRandom
import java.time.Instant

/**
 * Un annuaire en mémoire, qui tient les règles de `docs/protocole.md` §2 sans
 * aucun réseau.
 *
 * # Pourquoi il existe, et ce qu'il n'est pas
 *
 * Le transport de ce produit — HTTP/3 sur QUIC, authentification liée au canal
 * TLS — vit dans `asl-client` et n'est pas encore embarqué ici. Attendre qu'il
 * le soit pour écrire les écrans les aurait fait attendre ; les écrire contre
 * une interface qui ment aurait produit des écrans à jeter.
 *
 * Cette classe tient donc **les mêmes refus que le serveur** : un appareil ne
 * se révoque pas lui-même (`403`), un alias pris rend `409`, un objet absent et
 * un objet d'un autre compte rendent le même `404`, une nouvelle annonce du
 * même nom remplace la précédente. Elle ne fait rien de plus, et surtout elle
 * **ne vérifie aucune signature** : ce n'est pas un serveur, c'est un banc.
 *
 * **Elle part vide.** [Demonstration] la remplit de ce que les maquettes
 * montraient, pour qu'un écran ait quelque chose à afficher.
 */
class AnnuaireSimule(
    /** L'heure vient de l'extérieur : un essai la fixe, l'application la lit. */
    private val horloge: () -> Instant = { Instant.now() },
) : Annuaire {
    private val verrou = Mutex()
    private var compteLocal: Compte? = null
    private val parcMachines = mutableListOf<Machine>()
    private val parcAppareils = mutableListOf<Appareil>()
    private val aretes = mutableListOf<Autorisation>()
    /** Les autres comptes que cet annuaire connaît : identifiant → alias. */
    private val autresComptes = mutableMapOf<Identifiant, String?>()
    private val alea = SecureRandom()

    // ── Compte ────────────────────────────────────────────────────────────────

    /** Le défi en cours. Un seul, et consommé par la première preuve qui le couvre : un défi rejoué n'est plus un défi. */
    private var defiEnCours: ByteArray? = null

    override suspend fun defi(): ByteArray = verrou.withLock {
        ByteArray(Messages.DEFI_OCTETS).also(alea::nextBytes).also { defiEnCours = it }
    }

    /** Il n'y a pas de canal : trente-deux zéros, et le banc le dit. Un transport réel dérive cette valeur de sa connexion TLS. */
    override suspend fun liaisonDeCanal(): ByteArray = ByteArray(Messages.LIAISON_OCTETS)

    override suspend fun ouvrirCompte(cle: ByteArray, preuve: ByteArray): Compte = verrou.withLock {
        compteLocal?.let { return it }
        // Le banc vérifie la preuve comme le serveur le fera : sous la clé
        // présentée, sur le défi qu'il a émis. C'est la seule cryptographie
        // qu'il fait, et c'est celle qui éprouve la clé de l'appareil.
        val defi = defiEnCours ?: throw ErreurAnnuaire.RequeteInvalide("aucun défi en cours")
        defiEnCours = null
        val message = Messages.dePossession(cle, defi, ByteArray(Messages.LIAISON_OCTETS))
        if (!P256.verifie(cle, message, preuve)) throw ErreurAnnuaire.PreuveInvalide
        val compte = Compte(neuf(Genre.UTILISATEUR))
        compteLocal = compte
        parcAppareils += Appareil(neuf(Genre.APPAREIL), "Cet appareil", Appareil.Biometrie.EMPREINTE, horloge(), estCeluiCi = true)
        compte
    }

    override suspend fun compte(): Compte? = verrou.withLock { compteLocal }

    override suspend fun definirAlias(alias: String?) = verrou.withLock {
        val compte = compteLocal ?: throw ErreurAnnuaire.Introuvable
        if (alias != null) {
            if (!aliasValide(alias)) throw ErreurAnnuaire.RequeteInvalide("alias")
            if (autresComptes.values.any { it.equals(alias, ignoreCase = true) }) throw ErreurAnnuaire.AliasPris
        }
        compteLocal = compte.copy(alias = alias)
    }

    // ── Machines ──────────────────────────────────────────────────────────────

    override suspend fun machines(): List<Machine> = verrou.withLock { parcMachines.toList() }

    override suspend fun declarerMachine(nom: String, capacites: Set<Capacite>): Machine = verrou.withLock {
        if (compteLocal == null) throw ErreurAnnuaire.Introuvable
        if (!Machine.nomValide(nom)) throw ErreurAnnuaire.RequeteInvalide("nom")
        val machine = Machine(neuf(Genre.MACHINE), nom, capacites, Machine.Cle.Attendue(code()))
        parcMachines += machine
        machine
    }

    override suspend fun modifierMachine(id: Identifiant, nom: String?, capacites: Set<Capacite>?): Machine = verrou.withLock {
        if (nom == null && capacites == null) throw ErreurAnnuaire.RequeteInvalide("{}")
        val indice = indiceMachine(id)
        var machine = parcMachines[indice]
        if (nom != null) {
            if (!Machine.nomValide(nom)) throw ErreurAnnuaire.RequeteInvalide("nom")
            machine = machine.copy(nom = nom)
        }
        if (capacites != null) {
            // Retirer la capacité d'annonce ferme les connexions, donc fait
            // tomber les baux : les services partent, comme à un arrêt.
            if (Capacite.ANNONCE in machine.capacites && Capacite.ANNONCE !in capacites) machine = fermerConnexions(machine)
            machine = machine.copy(capacites = capacites)
        }
        parcMachines[indice] = machine
        machine
    }

    override suspend fun emettreCode(machine: Identifiant): CodeEnrolement = verrou.withLock {
        val indice = indiceMachine(machine)
        val code = code()
        val actuelle = parcMachines[indice]
        parcMachines[indice] = when (val cle = actuelle.cle) {
            is Machine.Cle.Attendue -> actuelle.copy(cle = Machine.Cle.Attendue(code))
            is Machine.Cle.Enrolee -> throw ErreurAnnuaire.RequeteInvalide("la machine est déjà enrôlée")
            is Machine.Cle.Revoquee -> actuelle.copy(cle = cle.copy(code = code))
        }
        code
    }

    override suspend fun revoquerCle(machine: Identifiant) = verrou.withLock {
        val indice = indiceMachine(machine)
        val actuelle = parcMachines[indice]
        if (actuelle.cle !is Machine.Cle.Enrolee) throw ErreurAnnuaire.Introuvable
        parcMachines[indice] = fermerConnexions(actuelle).copy(cle = Machine.Cle.Revoquee(horloge()))
    }

    private fun indiceMachine(id: Identifiant): Int =
        parcMachines.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw ErreurAnnuaire.Introuvable

    private fun fermerConnexions(machine: Machine): Machine {
        val instant = horloge()
        return machine.copy(services = machine.services.map {
            it.copy(etat = Service.Etat.Parti(volontaire = false, le = instant), joignabilite = emptyMap())
        })
    }

    // ── Appareils ─────────────────────────────────────────────────────────────

    override suspend fun appareils(): List<Appareil> = verrou.withLock { parcAppareils.toList() }

    override suspend fun revoquerAppareil(id: Identifiant) = verrou.withLock {
        val indice = parcAppareils.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw ErreurAnnuaire.Introuvable
        if (parcAppareils[indice].estCeluiCi) throw ErreurAnnuaire.Interdit
        parcAppareils[indice] = parcAppareils[indice].copy(revoqueLe = horloge())
    }

    // ── Autorisations ─────────────────────────────────────────────────────────

    override suspend fun autorisations(): List<Autorisation> = verrou.withLock { aretes.toList() }

    override suspend fun utilisateurExiste(id: Identifiant): Boolean = verrou.withLock { existe(id) }

    private fun existe(id: Identifiant) = id == compteLocal?.identifiant || autresComptes.containsKey(id)

    override suspend fun identifiantPourAlias(alias: String): Identifiant? = verrou.withLock {
        compteLocal?.takeIf { it.alias.equals(alias, ignoreCase = true) }?.identifiant
            ?: autresComptes.entries.firstOrNull { it.value.equals(alias, ignoreCase = true) }?.key
    }

    override suspend fun accorder(beneficiaire: Identifiant, portee: Autorisation.Portee, etiquette: String): Autorisation = verrou.withLock {
        val compte = compteLocal ?: throw ErreurAnnuaire.Introuvable
        if (!existe(beneficiaire)) throw ErreurAnnuaire.Introuvable
        when (portee) {
            is Autorisation.Portee.Tout -> Unit
            is Autorisation.Portee.Machine -> indiceMachine(portee.id)
            is Autorisation.Portee.Service ->
                if (parcMachines.none { m -> m.services.any { it.id == portee.id } }) throw ErreurAnnuaire.Introuvable
        }
        val arete = Autorisation(neuf(Genre.AUTORISATION), compte.identifiant, beneficiaire, portee, etiquette, horloge())
        aretes += arete
        arete
    }

    override suspend fun revoquerAutorisation(id: Identifiant) = verrou.withLock {
        val indice = aretes.indexOfFirst { it.id == id && it.accordeePar == compteLocal?.identifiant }.takeIf { it >= 0 }
            ?: throw ErreurAnnuaire.Introuvable
        aretes[indice] = aretes[indice].copy(revoqueeLe = horloge())
    }

    // ── Ce qu'un annuaire de démonstration porte ──────────────────────────────

    /** Fait arriver une annonce, comme un daemon le ferait. Une annonce du même nom **remplace** la précédente. */
    suspend fun annoncer(machine: Identifiant, service: Service) = verrou.withLock {
        val indice = indiceMachine(machine)
        val actuelle = parcMachines[indice]
        if (Capacite.ANNONCE !in actuelle.capacites) throw ErreurAnnuaire.Interdit
        val services = actuelle.services.toMutableList()
        val existant = services.indexOfFirst { it.nom == service.nom }
        if (existant >= 0) services[existant] = service else services += service
        parcMachines[indice] = actuelle.copy(services = services)
    }

    /** Fait exister un autre compte, avec ou sans alias. */
    suspend fun inscrireAutreCompte(id: Identifiant, alias: String?) = verrou.withLock { autresComptes[id] = alias }

    /** Reçoit une autorisation accordée par un autre compte. */
    suspend fun recevoir(autorisation: Autorisation) = verrou.withLock { aretes += autorisation }

    /** Pose une machine ou un appareil directement — pour un banc. */
    suspend fun poser(machine: Machine) = verrou.withLock { parcMachines += machine }
    suspend fun poser(appareil: Appareil) = verrou.withLock { parcAppareils += appareil }

    private fun neuf(genre: Genre) = Identifiant(genre, ByteArray(16).also(alea::nextBytes))
    private fun code() = CodeEnrolement.depuisEntropie(ByteArray(8).also(alea::nextBytes), horloge())

    companion object {
        /** Un alias se compare : ASCII, lettres, chiffres, tiret, 3 à 32. */
        fun aliasValide(alias: String): Boolean =
            alias.length in 3..32 && alias.all { it.code < 128 && (it.isLetterOrDigit() || it == '-') }
    }
}
