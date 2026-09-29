package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.CodeInvitation
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.MachineDuDomaine
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.DetailDuDomaine
import org.airdesktop.servicelocator.modele.CodeDInscription
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.MachineVisible
import org.airdesktop.servicelocator.modele.Messages
import org.airdesktop.servicelocator.modele.P256
import org.airdesktop.servicelocator.modele.PointDePoussee
import org.airdesktop.servicelocator.modele.NonConfirmeException
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.modele.Signataire
import java.security.SecureRandom
import java.time.Instant

/**
 * Un annuaire en mémoire, qui tient les règles de `docs/protocole.md` §2 sans
 * aucun réseau.
 *
 * # Pourquoi il existe, et ce qu'il n'est pas
 *
 * Le transport de ce produit — HTTP/3 sur QUIC, authentification liée au canal
 * TLS — vit dans `asl-client` et c'est `AnnuaireReel` qui l'embarque. Les
 * écrans ont été écrits avant lui, contre ce banc : les écrire contre une
 * interface qui ment aurait produit des écrans à jeter. Il reste pour les
 * essais, et pour faire tourner l'application sans annuaire sous la main.
 *
 * Cette classe tient donc **les mêmes refus que le serveur** : un appareil ne
 * se révoque pas lui-même (`403`), un alias pris rend `409`, un objet absent et
 * un objet d'un autre compte rendent le même `404`, une nouvelle annonce du
 * même nom remplace la précédente. Elle ne fait rien de plus, et la seule
 * cryptographie qu'elle fait est de vérifier la preuve de possession : ce
 * n'est pas un serveur, c'est un banc.
 *
 * **Elle part vide.** [Demonstration] la remplit de ce que les maquettes
 * montraient, pour qu'un écran ait quelque chose à afficher.
 */
class AnnuaireSimule(
    /**
     * Sous quelle condition ce banc laisse ouvrir un compte. [Posture.Facultative]
     * par défaut — c'est ce que font les racines d'air-desktop-project, et un
     * banc qui exigerait un code sans qu'on le demande serait une surprise.
     */
    private val posture: Posture = Posture.Facultative,
    /**
     * Les codes que ce banc honore sous [Posture.Invitation], sous leur forme
     * canonique. **Consommer, c'est retirer** — la règle du serveur, et la
     * seule qui tienne l'usage unique (`protocole.md` §2.2).
     */
    invitations: Set<String> = emptySet(),
    /**
     * L'heure vient de l'extérieur : un essai la fixe, l'application la lit.
     *
     * **Dernière, et elle doit le rester** : les essais écrivent
     * `AnnuaireSimule { instant }`, et un paramètre ajouté après elle
     * capturerait cette lambda.
     */
    private val horloge: () -> Instant = { Instant.now() },
) : Annuaire {
    private val invitationsVivantes = invitations.toMutableSet()
    private val verrou = Mutex()
    private var compteLocal: Compte? = null
    private val parcMachines = mutableListOf<Machine>()
    private val parcAppareils = mutableListOf<Appareil>()
    /** La clé sous laquelle chaque appareil enrôlé d'ici est entré — ce que [rejoindre] recoupe. */
    private val clesEnrolees = mutableMapOf<Identifiant, ByteArray>()
    /** Un point de poussée par appareil, au plus : le neuf remplace l'ancien. */
    private val points = mutableMapOf<Identifiant, String>()
    private val aretes = mutableListOf<Autorisation>()
    /** Les autres comptes que cet annuaire connaît : identifiant → alias. */
    private val autresComptes = mutableMapOf<Identifiant, String?>()
    private val alea = SecureRandom()

    /** Un domaine du compte : son alias, ce qui l'héberge (`null` : les racines). Supprimé, il sort de la liste. */
    private data class DomaineRange(val id: Identifiant, var alias: String?, var hebergePar: Identifiant?)
    private val parcDomaines = mutableListOf<DomaineRange>()
    /** Machine → domaine où elle est rangée. Une machine dans un domaine au plus (décision 30). */
    private val rattachements = mutableMapOf<Identifiant, Identifiant>()
    /** Mes annuaires locaux, membre par membre, et mes déclarations qui attendent — ce que `GET /v1/annuaires` rend. */
    private val mesInscriptions = mutableListOf<Inscription>()
    /** Les codes émis ici qui ne se sont pas encore présentés : code → la ligne `attendue` qu'ils remplaceront. */
    private val codesDInscription = mutableMapOf<String, Pair<Identifiant?, Inscription>>()
    /** Ce banc administre-t-il les racines ? Faux par défaut : `GET /v1/inscriptions` rend alors `404`. */
    private var administrateur = false
    /** Les inscriptions qui attendent un administrateur des racines. */
    private val enAttente = mutableListOf<Inscription>()

    companion object {
        /** Il n'y a pas de canal : trente-deux zéros, et le banc le dit. Un transport réel dérive cette valeur de sa connexion TLS. */
        val LIAISON_DE_CANAL = ByteArray(Messages.LIAISON_OCTETS)

    }

    // ── Compte ────────────────────────────────────────────────────────────────

    override suspend fun ouvrirCompte(signataire: Signataire, invitation: CodeInvitation?): Compte {
        verrou.withLock { compteLocal }?.let { return it }
        // **LA POSTURE D'ABORD** : sous `invitation`, le serveur n'admet que
        // la plate-forme `3`, et consomme le code dans la transaction qui crée
        // le compte. Le banc tient la même règle, y compris le refus unique —
        // un code faux, expiré ou déjà servi ne se distinguent pas.
        if (posture == Posture.Invitation) {
            val code = invitation ?: throw ErreurAnnuaire.InvitationRefusee
            verrou.withLock {
                if (!invitationsVivantes.contains(code.symboles)) throw ErreurAnnuaire.InvitationRefusee
            }
        }
        // Un défi neuf, à usage unique, puis la preuve — signée par le
        // signataire, sur le message que le serveur recomposera. Hors du
        // verrou : signer, c'est attendre le porteur.
        val defi = ByteArray(Messages.DEFI_OCTETS).also(alea::nextBytes)
        val cle = signataire.clePublique
        val message = Messages.dePossession(cle, defi, LIAISON_DE_CANAL)
        val preuve = try {
            signataire.signer(message)
        } catch (e: NonConfirmeException) {
            throw ErreurAnnuaire.NonConfirme
        }
        return verrou.withLock {
            compteLocal?.let { return it }
            // Le banc vérifie la preuve comme le serveur le fera : sous la clé
            // présentée, sur ce défi-là. C'est la seule cryptographie qu'il
            // fait, et c'est celle qui éprouve la clé de l'appareil.
            if (!P256.verifie(cle, message, preuve)) throw ErreurAnnuaire.PreuveInvalide
            // Consommer, c'est retirer : le même code ne rouvrira pas un compte.
            if (posture == Posture.Invitation && invitation != null) invitationsVivantes.remove(invitation.symboles)
            val compte = Compte(neuf(Genre.UTILISATEUR))
            compteLocal = compte
            // Le premier domaine naît avec le compte, dans la même transaction (décision 30).
            parcDomaines += DomaineRange(neuf(Genre.DOMAINE), null, null)
            parcAppareils += Appareil(neuf(Genre.APPAREIL), "Cet appareil", Appareil.Biometrie.EMPREINTE, horloge(), estCeluiCi = true)
            compte
        }
    }

    /** Pas de canal, donc pas de défi à tirer avant la clé : le banc montre la clé du signataire, et c'est tout. */
    override suspend fun clePourRejoindre(signataire: () -> Signataire): ByteArray = signataire().clePublique

    override suspend fun annulerRejoindre() = Unit

    override suspend fun rejoindre(compte: Identifiant, appareil: Identifiant, signataire: Signataire): Compte {
        // Le banc ne connaît qu'un compte, et les appareils qu'on y a enrôlés
        // avec leur clé : l'invitation doit désigner l'un d'eux, sous la clé
        // que le signataire présente. Puis la preuve, comme le serveur
        // l'exigerait à la connexion : le message d'authentification, sous le
        // genre `a`. Hors du verrou : signer, c'est attendre le porteur.
        val local = verrou.withLock {
            val local = compteLocal ?: throw ErreurAnnuaire.Introuvable
            if (local.identifiant != compte) throw ErreurAnnuaire.Introuvable
            if (parcAppareils.none { it.id == appareil && it.revoqueLe == null }) throw ErreurAnnuaire.Introuvable
            if (clesEnrolees[appareil]?.contentEquals(signataire.clePublique) != true) throw ErreurAnnuaire.Introuvable
            local
        }
        val defi = ByteArray(Messages.DEFI_OCTETS).also(alea::nextBytes)
        val message = Messages.aSigner(appareil, defi, LIAISON_DE_CANAL)
        val preuve = try {
            signataire.signer(message)
        } catch (e: NonConfirmeException) {
            throw ErreurAnnuaire.NonConfirme
        }
        if (!P256.verifie(signataire.clePublique, message, preuve)) throw ErreurAnnuaire.PreuveInvalide
        return verrou.withLock {
            // Désormais, c'est CET appareil qui regarde l'écran. Et il entre
            // sous ce que sa clé porte : une chaîne d'attestation le fait
            // `android`, comme `POST /v1/attestation` le ferait — sans la juger,
            // le banc n'a pas de racine à lui opposer ; sans chaîne, `aucune`.
            val entree = if (signataire.attestation != null) Appareil.Attestation.ANDROID else Appareil.Attestation.AUCUNE
            for (i in parcAppareils.indices) {
                val celuiCi = parcAppareils[i].id == appareil
                parcAppareils[i] = parcAppareils[i].copy(
                    estCeluiCi = celuiCi, nom = if (celuiCi) "Cet appareil" else parcAppareils[i].nom,
                    attestation = if (celuiCi) entree else parcAppareils[i].attestation,
                )
            }
            local
        }
    }

    override suspend fun compte(): Compte? = verrou.withLock { compteLocal }

    /**
     * Ce que le serveur fait dans une transaction, le banc le fait sous son
     * verrou : le compte, ses appareils et leurs clés, ses machines et leurs
     * services, les autorisations dans les deux sens, l'alias — tout part. Les
     * autres comptes restent : ils ne sont pas à nous. Un second appel rend
     * `401`, comme sur le fil.
     */
    override suspend fun effacerCompte() = verrou.withLock {
        if (compteLocal == null) throw ErreurAnnuaire.NonReconnu
        compteLocal = null
        parcAppareils.clear()
        clesEnrolees.clear()
        points.clear()
        parcMachines.clear()
        aretes.clear()
        parcDomaines.clear()
        rattachements.clear()
        mesInscriptions.clear()
        codesDInscription.clear()
    }

    override suspend fun definirAlias(alias: String?) = verrou.withLock {
        val compte = compteLocal ?: throw ErreurAnnuaire.Introuvable
        val range = alias?.let { Alias.pourCompte(it) ?: throw ErreurAnnuaire.RequeteInvalide("alias") }
        // Unique, et sensible à la casse : « Thierry » n'empêche pas « thierry ».
        if (range != null && autresComptes.values.any { it == range }) throw ErreurAnnuaire.AliasPris
        compteLocal = compte.copy(alias = range)
    }

    // ── Machines ──────────────────────────────────────────────────────────────

    override suspend fun machines(): List<Machine> = verrou.withLock { parcMachines.toList() }

    override suspend fun declarerMachine(nom: String, capacites: Set<Capacite>): Machine = verrou.withLock {
        if (compteLocal == null) throw ErreurAnnuaire.Introuvable
        val range = Machine.nomDHote(nom) ?: throw ErreurAnnuaire.RequeteInvalide("nom")
        val machine = Machine(neuf(Genre.MACHINE), range, capacites, Machine.Cle.Attendue(code()))
        parcMachines += machine
        machine
    }

    override suspend fun modifierMachine(id: Identifiant, nom: String?, capacites: Set<Capacite>?): Machine = verrou.withLock {
        if (nom == null && capacites == null) throw ErreurAnnuaire.RequeteInvalide("{}")
        val indice = indiceMachine(id)
        var machine = parcMachines[indice]
        if (nom != null) {
            machine = machine.copy(nom = Machine.nomDHote(nom) ?: throw ErreurAnnuaire.RequeteInvalide("nom"))
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

    override suspend fun definirAliasMachine(id: Identifiant, alias: String?): Machine = verrou.withLock {
        val indice = indiceMachine(id)
        val range = alias?.let { Alias.pourMachine(it) ?: throw ErreurAnnuaire.RequeteInvalide("alias") }
        parcMachines[indice] = parcMachines[indice].copy(alias = range)
        parcMachines[indice]
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

    override suspend fun enrolerAppareil(cle: ByteArray): Appareil = verrou.withLock {
        if (compteLocal == null) throw ErreurAnnuaire.Introuvable
        // Le serveur vérifie que la clé est un point de la courbe ; le banc,
        // qu'elle en a la forme. Une clé déjà enrôlée ne s'enrôle pas deux fois.
        if (cle.size != Messages.CLE_OCTETS || (cle[0] != 0x02.toByte() && cle[0] != 0x03.toByte())) throw ErreurAnnuaire.RequeteInvalide("clé")
        if (clesEnrolees.values.any { it.contentEquals(cle) }) throw ErreurAnnuaire.RequeteInvalide("clé déjà enrôlée")
        val appareil = Appareil(neuf(Genre.APPAREIL), "Autre appareil", Appareil.Biometrie.EMPREINTE, horloge())
        parcAppareils += appareil
        clesEnrolees[appareil.id] = cle
        appareil
    }

    override suspend fun revoquerAppareil(id: Identifiant) = verrou.withLock {
        val indice = parcAppareils.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw ErreurAnnuaire.Introuvable
        if (parcAppareils[indice].estCeluiCi) throw ErreurAnnuaire.Interdit
        parcAppareils[indice] = parcAppareils[indice].copy(revoqueLe = horloge())
        // Le point part avec l'appareil, dans la même écriture.
        points.remove(id)
        Unit
    }

    /** Pour soi seulement : le banc, comme le serveur, ne connaît que l'appareil qui parle. Sans compte, personne à décrire. */
    override suspend fun decrire(description: Appareil.Description) = verrou.withLock {
        val indice = parcAppareils.indexOfFirst { it.estCeluiCi }.takeIf { it >= 0 } ?: throw ErreurAnnuaire.Introuvable
        val octets = description.modele.toByteArray(Charsets.UTF_8).size
        if (octets !in 1..Appareil.Description.MODELE_OCTETS_MAX) throw ErreurAnnuaire.RequeteInvalide("modele")
        parcAppareils[indice] = parcAppareils[indice].copy(description = description)
    }

    /**
     * Pour soi seulement, et sous la forme que le serveur exige — le banc la
     * juge avec les mêmes règles, et refuse comme lui : `400`, la règle dite.
     */
    override suspend fun deposerPoint(point: String) = verrou.withLock {
        val moi = parcAppareils.firstOrNull { it.estCeluiCi && it.revoqueLe == null } ?: throw ErreurAnnuaire.Introuvable
        PointDePoussee.refus(point)?.let { throw ErreurAnnuaire.RequeteInvalide("point de poussée — $it") }
        points[moi.id] = point
        Unit
    }

    /** Le point que cet appareil a déposé, ce que le serveur range — pour un essai. */
    suspend fun pointDe(appareil: Identifiant): String? = verrou.withLock { points[appareil] }

    // ── Autorisations ─────────────────────────────────────────────────────────

    override suspend fun autorisations(): List<Autorisation> = verrou.withLock { aretes.toList() }

    /** La règle du serveur : mes machines si c'est moi ; le banc n'a de machines que pour le compte local, les autres rendent vide. */
    override suspend fun machinesDe(utilisateur: Identifiant): List<MachineVisible> = verrou.withLock {
        val moi = compteLocal ?: throw ErreurAnnuaire.Introuvable
        if (utilisateur == moi.identifiant) parcMachines.map { MachineVisible(it.id, it.nom, it.alias) } else emptyList()
    }

    /** Combien de fois [fermer] a été appelé — ce qu'un essai de bascule vérifie. */
    var fermetures = 0
        private set

    /** Le banc ne tient ni connexion ni handle : fermer ne fait que se compter. */
    /**
     * Le banc n'a pas de connexion : il se dit « joint » d'emblée, sous un nom
     * qui ne se confond avec aucune racine, et « perdu » une fois fermé.
     */
    private val suivi = SuiviDeLaRacine(RacineJointe("en mémoire", "banc en mémoire"))
    override val racineJointe: StateFlow<RacineJointe?> = suivi.racine

    override suspend fun fermer() = verrou.withLock {
        fermetures += 1
        suivi.perdue()
    }

    /** Le banc dit ce qu'il est, pour que l'écran ne confonde jamais une démonstration avec un annuaire. */
    override suspend fun annonce(): Annonce = Annonce("banc en mémoire", posture)

    override suspend fun utilisateurExiste(id: Identifiant): Boolean = verrou.withLock { existe(id) }

    private fun existe(id: Identifiant) = id == compteLocal?.identifiant || autresComptes.containsKey(id)

    override suspend fun identifiantPourAlias(alias: String): Identifiant? = verrou.withLock {
        val cherche = Alias.nfc(alias)
        compteLocal?.takeIf { it.alias == cherche }?.identifiant
            ?: autresComptes.entries.firstOrNull { it.value == cherche }?.key
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

    // ── Domaines et annuaires locaux ─────────────────────────────────────────

    private fun moi(): Identifiant = compteLocal?.identifiant ?: throw ErreurAnnuaire.NonReconnu

    private fun domaineVu(d: DomaineRange, moi: Identifiant) = Domaine(
        id = d.id, proprietaire = moi, alias = d.alias, hebergePar = d.hebergePar,
        droits = setOf(Domaine.ADMINISTRER, Domaine.RATTACHER, Domaine.VOIR, Domaine.LOCALISER),
    )

    override suspend fun domaines(): List<Domaine> = verrou.withLock {
        val moi = moi()
        parcDomaines.map { domaineVu(it, moi) }
    }

    override suspend fun domaine(id: Identifiant): DetailDuDomaine = verrou.withLock {
        val moi = moi()
        val d = parcDomaines.firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable
        val machines = parcMachines.filter { rattachements[it.id] == id }.map { MachineDuDomaine(it.id, moi, it.nom, it.alias) }
        DetailDuDomaine(domaineVu(d, moi), machines)
    }

    override suspend fun creerDomaine(alias: String?): Identifiant = verrou.withLock {
        moi()
        val range = alias?.let { Alias.pourDomaine(it) ?: throw ErreurAnnuaire.RequeteInvalide("alias") }
        val id = neuf(Genre.DOMAINE)
        parcDomaines += DomaineRange(id, range, null)
        id
    }

    override suspend fun definirAliasDomaine(id: Identifiant, alias: String?) = verrou.withLock {
        moi()
        val range = alias?.let { Alias.pourDomaine(it) ?: throw ErreurAnnuaire.RequeteInvalide("alias") }
        (parcDomaines.firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable).alias = range
    }

    override suspend fun supprimerDomaine(id: Identifiant) = verrou.withLock {
        moi()
        val d = parcDomaines.firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable
        if (parcDomaines.size == 1) throw ErreurAnnuaire.DernierDomaine
        parcDomaines.remove(d)
        rattachements.entries.removeIf { it.value == id }
        Unit
    }

    override suspend fun rattacher(machine: Identifiant, domaine: Identifiant?) = verrou.withLock {
        moi()
        if (parcMachines.none { it.id == machine }) throw ErreurAnnuaire.Introuvable
        if (domaine == null) { rattachements.remove(machine); return@withLock }
        if (parcDomaines.none { it.id == domaine }) throw ErreurAnnuaire.Introuvable
        rattachements[machine] = domaine
    }

    /** Le banc ne tient qu'un compte : ses machines rendent tout, une machine inconnue `[]` — comme l'annuaire sans droit. */
    override suspend fun servicesDe(machine: Identifiant): List<Service> = verrou.withLock {
        moi()
        parcMachines.firstOrNull { it.id == machine }?.services.orEmpty()
    }

    /** Le domaine où une machine est rangée — pour l'écran d'une machine, et pour un banc. */
    suspend fun domaineDe(machine: Identifiant): Identifiant? = verrou.withLock { rattachements[machine] }

    override suspend fun annuairesLocaux(): List<Inscription> = verrou.withLock { moi(); mesInscriptions.toList() }

    private fun emettreCodeDInscription(annuaire: Identifiant?, adresse: String): CodeDInscription {
        val forme = Alias.adresseDAnnuaire(adresse) ?: throw ErreurAnnuaire.RequeteInvalide("adresse")
        val code = CodeDInscription(code().symboles, horloge().plusSeconds(24 * 3600))
        val attendue = Inscription(EtatDInscription.Attendue, "attendue", null, annuaire, null, forme, code.expireA)
        mesInscriptions += attendue
        codesDInscription[code.code] = annuaire to attendue
        return code
    }

    override suspend fun declarerAnnuaire(adresse: String): CodeDInscription = verrou.withLock {
        moi()
        emettreCodeDInscription(null, adresse)
    }

    override suspend fun declarerSecondMembre(annuaire: Identifiant, adresse: String): CodeDInscription = verrou.withLock {
        moi()
        val membres = mesInscriptions.filter { it.annuaire == annuaire && it.etat in setOf(EtatDInscription.EnAttente, EtatDInscription.Acceptee) }
        if (membres.none { it.estTitulaire && it.etat == EtatDInscription.Acceptee }) throw ErreurAnnuaire.Introuvable
        if (membres.size >= 2) throw ErreurAnnuaire.PaireComplete
        emettreCodeDInscription(annuaire, adresse)
    }

    override suspend fun retirerAnnuaire(annuaire: Identifiant) = verrou.withLock {
        moi()
        if (mesInscriptions.none { it.annuaire == annuaire }) throw ErreurAnnuaire.Introuvable
        mesInscriptions.replaceAll { if (it.annuaire == annuaire && it.membre != null) it.copy(etat = EtatDInscription.Retiree, motDeLEtat = "retirée") else it }
        // Ses domaines reviennent aux racines.
        parcDomaines.filter { it.hebergePar == annuaire }.forEach { it.hebergePar = null }
    }

    override suspend fun retirerMembre(annuaire: Identifiant, membre: Identifiant) {
        if (annuaire == membre) return retirerAnnuaire(annuaire)
        verrou.withLock {
            moi()
            val i = mesInscriptions.indexOfFirst { it.annuaire == annuaire && it.membre == membre }
            if (i < 0) throw ErreurAnnuaire.Introuvable
            mesInscriptions[i] = mesInscriptions[i].copy(etat = EtatDInscription.Retiree, motDeLEtat = "retirée")
        }
    }

    override suspend fun confier(domaine: Identifiant, annuaire: Identifiant?) = verrou.withLock {
        moi()
        val d = parcDomaines.firstOrNull { it.id == domaine } ?: throw ErreurAnnuaire.Introuvable
        if (annuaire != null && mesInscriptions.none { it.annuaire == annuaire && it.estTitulaire && it.etat == EtatDInscription.Acceptee }) {
            // Vers un annuaire de son propre compte, accepté : sinon `404` (décision 48).
            throw ErreurAnnuaire.Introuvable
        }
        d.hebergePar = annuaire
    }

    override suspend fun inscriptionsEnAttente(): List<Inscription>? = verrou.withLock {
        if (administrateur) enAttente.filter { it.etat == EtatDInscription.EnAttente } else null
    }

    override suspend fun decider(membre: Identifiant, accepte: Boolean) = verrou.withLock {
        if (!administrateur) throw ErreurAnnuaire.Introuvable
        val i = enAttente.indexOfFirst { it.membre == membre }
        if (i < 0) throw ErreurAnnuaire.Introuvable
        val actuelle = enAttente[i]
        // Le refus l'emporte ; accepter une inscription refusée ou retirée, `409` (décision 51).
        if (accepte && actuelle.etat in setOf(EtatDInscription.Refusee, EtatDInscription.Retiree)) throw ErreurAnnuaire.InscriptionTranchee
        val etat = if (accepte) EtatDInscription.Acceptee else EtatDInscription.Refusee
        enAttente[i] = actuelle.copy(etat = etat, motDeLEtat = etat.mot)
        val j = mesInscriptions.indexOfFirst { it.membre == membre }
        if (j >= 0) mesInscriptions[j] = mesInscriptions[j].copy(etat = etat, motDeLEtat = etat.mot)
    }

    /**
     * Ce que fait `asl-server --register <code>` sur la machine : le code se présente sous la clé [membre]. Pour un
     * banc — la ligne `attendue` devient `en attente`, et l'inscription attend un administrateur.
     */
    suspend fun presenter(code: String, membre: Identifiant) = verrou.withLock {
        val (annuaire, attendue) = codesDInscription.remove(code) ?: throw ErreurAnnuaire.Introuvable
        val presentee = attendue.copy(
            etat = EtatDInscription.EnAttente, motDeLEtat = "en attente", membre = membre,
            annuaire = annuaire ?: membre, expireA = null,
        )
        mesInscriptions[mesInscriptions.indexOf(attendue)] = presentee
        enAttente += presentee.copy(proprietaire = compteLocal?.identifiant)
    }

    /** Fait de ce banc un administrateur des racines — pour un essai de l'écran d'administration. */
    suspend fun administrerLesRacines(oui: Boolean = true) = verrou.withLock { administrateur = oui }

    /** Fait exister un autre compte, avec ou sans alias. */
    suspend fun inscrireAutreCompte(id: Identifiant, alias: String?) = verrou.withLock { autresComptes[id] = alias }

    /** Reçoit une autorisation accordée par un autre compte. */
    suspend fun recevoir(autorisation: Autorisation) = verrou.withLock { aretes += autorisation }

    /** Pose une machine ou un appareil directement — pour un banc. */
    suspend fun poser(machine: Machine) = verrou.withLock { parcMachines += machine }
    suspend fun poser(appareil: Appareil) = verrou.withLock { parcAppareils += appareil }

    private fun neuf(genre: Genre) = Identifiant(genre, ByteArray(16).also(alea::nextBytes))
    private fun code() = CodeEnrolement.depuisEntropie(ByteArray(8).also(alea::nextBytes), horloge())

}
