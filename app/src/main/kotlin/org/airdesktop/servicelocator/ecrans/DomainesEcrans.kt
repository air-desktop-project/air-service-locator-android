package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Badge
import org.airdesktop.servicelocator.composants.BoutonCopier
import org.airdesktop.servicelocator.composants.BoutonDeGeste
import org.airdesktop.servicelocator.composants.BoutonDestructif
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.FeuilleDeSaisie
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneAGeste
import org.airdesktop.servicelocator.composants.NoteDeTuile
import org.airdesktop.servicelocator.composants.PiedDestructif
import org.airdesktop.servicelocator.composants.TexteAbsent
import org.airdesktop.servicelocator.composants.TexteFixe
import org.airdesktop.servicelocator.composants.TeteDeTuile
import org.airdesktop.servicelocator.composants.Tuile
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.composants.rememberGesteEnFeuille
import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.DetailDuDomaine
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.MachineDuDomaine
import org.airdesktop.servicelocator.reseau.RacinesConnues
import org.airdesktop.servicelocator.reseau.RacineDAnnuaire

// ── Les textes, à la lettre ceux d'iOS/macOS (`TextesDomaines`) ────────────────

/**
 * Les mots des domaines, des annuaires locaux et de l'administration des racines — **identiques sur les deux
 * plates-formes**, recopiés de `TextesDomaines` (application iOS/macOS). Un texte qui change change des deux côtés.
 */
internal object TextesDomaines {
    const val domaines = "Domaines"
    const val aucunDomaine = "Aucun domaine."
    const val creer = "Créer un domaine"
    const val aliasFacultatif = "Alias (facultatif)"
    const val hebergeRacines = "Hébergé par : les racines"
    fun hebergeAnnuaire(n: Identifiant) = "Hébergé par : l'annuaire ${n.texte}"
    const val pasDAlias = "pas d'alias"
    const val proprietaire = "Propriétaire"
    const val vous = "vous"
    const val supprimer = "Supprimer le domaine"
    const val confirmerSuppression = "Les machines qui y sont rangées n'y seront plus. Rien d'autre ne part."
    const val machinesRangees = "Machines rangées ici"
    const val aucuneMachine = "Aucune machine rangée dans ce domaine."
    const val domaineDeLaMachine = "Domaine"
    const val aucun = "aucun"
    const val ranger = "Ranger dans un domaine"
    const val retirerDuDomaine = "Retirer du domaine"
    const val confier = "Confier à mon annuaire local"
    const val rendreAuxRacines = "Rendre aux racines"
    const val annuaireLocal = "Mon annuaire local"
    const val aucunAnnuaire = "Aucun annuaire local déclaré."
    const val declarer = "Déclarer un annuaire local"
    const val adresse = "Adresse (hôte:port)"
    const val adresseAide = "L'adresse où la machine qui l'héberge écoute."
    const val codeTitre = "Code d'inscription"
    const val codeAide = "À présenter sur la machine dans les 24 heures :"
    /**
     * `racine` : la racine à laquelle l'application parle, sous la forme `locateur=n-…` que le serveur
     * croit par sa clé (décision 58) — plus d'autorité `--ca`. `<clé>` reste littéral.
     */
    fun commande(code: String, racine: String) = "asl-server --register $code --directory $racine --identity-key <clé>"
    const val secondMembre = "Déclarer le second membre de la paire"
    const val retirer = "Retirer l'annuaire"
    const val confirmerRetrait = "La paire entière est retirée ; les domaines qu'elle héberge reviennent aux racines."
    const val administration = "Administration des racines"
    const val aucuneInscription = "Aucune inscription en attente."
    const val accepter = "Accepter"
    const val refuser = "Refuser"
    fun confirmerAcceptation(membre: String, adresse: String, proprietaire: String) =
        "L'annuaire $membre ($adresse), du compte $proprietaire, servira les domaines qu'on lui confiera."
    const val confirmerRefus = "L'annuaire ne pourra pas servir de domaine. Un refus l'emporte même sur une acceptation passée."

    /** Le mot d'un état d'inscription ; un état inconnu, tel que l'annuaire l'a dit. */
    fun etat(inscription: Inscription): String = when (inscription.etat) {
        EtatDInscription.Attendue -> "code pas encore présenté"
        EtatDInscription.EnAttente -> "en attente de la décision des racines"
        EtatDInscription.Acceptee -> "acceptée"
        EtatDInscription.Refusee -> "refusée"
        EtatDInscription.Retiree -> "retirée"
        EtatDInscription.Inconnu -> inscription.motDeLEtat
    }
}

/** Ce qu'on dit de ce qui héberge un domaine. */
internal fun libelleHebergeur(domaine: Domaine): String =
    domaine.hebergePar?.let { TextesDomaines.hebergeAnnuaire(it) } ?: TextesDomaines.hebergeRacines

/** Ce qu'une ligne de la liste en dit : l'alias, ou l'identifiant entier, dit sans alias — l'abrégé ne distingue pas deux domaines. */
internal fun titreComplet(domaine: Domaine): String = domaine.alias ?: "${domaine.id.texte} - ${TextesDomaines.pasDAlias}"

/**
 * Qui sert un domaine, et à quelles adresses le joindre — la règle de l'application iOS (`Hebergement`).
 *
 * Les racines, ce sont celles que l'application connaît ([RacineDAnnuaire.identites]), chacune une fois avec ses
 * locateurs ; un annuaire local, ce sont les adresses déclarées de ses membres. **Ce qu'on ne sait pas, on ne
 * l'invente pas** : pas d'adresse, pas de ligne.
 */
internal data class Hebergement(val titre: String, val serveurs: List<Serveur>) {
    data class Serveur(val nom: String, val adresses: List<String>)

    companion object {
        fun de(domaine: Domaine, racines: List<RacineDAnnuaire>, locaux: List<Inscription>): Hebergement {
            val n = domaine.hebergePar
            val serveurs = if (n == null) {
                // Une même racine figure sous plusieurs entrées (« Automatique » et la sienne) : on la dit une fois.
                racines.flatMap { it.identites }.distinctBy { it.annuaire }.map { Serveur(RacinesConnues.nom(it.annuaire), it.locateurs) }
            } else {
                locaux.filter { it.annuaire == n }.map { Serveur((it.membre ?: n).abrege, listOf(it.adresse)) }
            }
            return Hebergement(libelleHebergeur(domaine), serveurs)
        }
    }
}

// ── La page des domaines, à la lettre le Mac ─────────────────────────────────

/**
 * Les mots des pages « Domaines » et d'un domaine — **recopiés de l'application Mac** (`DomainesFenetreVue.swift`,
 * PR iOS #36). Les mots communs à toutes les plates-formes restent dans [TextesDomaines].
 */
internal object TextesPageDomaines {
    const val introduction = "Les domaines que vous possédez, et ceux où l'un de vos groupes tient un droit."
    const val aucunDomaine = "Aucun domaine"
    const val aucunDomaineAide = "Un domaine range vos machines ; créez-en un avec « Créer un domaine… »."
    const val creerUnDomaine = "Créer un domaine…"
    const val renommer = "Renommer…"
    const val ouvrir = "Ouvrir"
    const val modifier = "Modifier…"
    const val changer = "Changer…"
    /** Le badge du domaine racine, en tête de sa tuile : il se range comme un autre, mais ne se confie pas. */
    const val domaineRacine = "Domaine racine"

    const val nom = "Nom"
    const val identifiant = "Identifiant"
    const val hebergePar = "Hébergé par"
    const val lesRacines = "Les racines"
    const val monAnnuaireLocal = "Mon annuaire local"
    const val lAnnuaireLocal = "L'annuaire local"
    const val mesDroits = "Mes droits"
    const val tenusParVosGroupes = "tenus par vos groupes"
    const val proprietaire = "Propriétaire"
    const val administrer = "Administrer"

    const val rangerIci = "Ranger une machine ici…"
    const val retirerDuDomaine = "Retirer du domaine…"
    const val aVous = "à vous"
    const val confirmerRetraitDeLaMachine = "Retirer cette machine du domaine ?"
    fun retirerLaMachine(titre: String) = "Retirer « $titre »"
    const val retraitDeLaMachine = "La machine reste à vous, hors de tout domaine ; ses services ne se résolvent plus sous celui-ci."
    const val suppression = "Supprimer détache ses machines ; son alias et ses groupes disparaissent. Votre dernier domaine ne se supprime pas."

    const val creerTitre = "Créer un domaine"
    const val creerExplication = "Un domaine range vos machines. Son alias est public et n'est pas unique ; il est facultatif."
    const val creer = "Créer"
    const val nomTitre = "Modifier le nom du domaine"
    const val nomExplication = "L'alias est public et n'est pas unique : deux domaines peuvent le porter. Espaces et lettres accentuées sont admis."
    const val alias = "Alias"
    const val domaine = "Domaine"
    const val enregistrer = "Enregistrer"
    const val retirerLAlias = "Retirer l'alias"
    fun hebergementTitre(titre: String) = "Qui sert « $titre » ?"
    const val hebergementExplication =
        "Les racines, ou l'un de vos annuaires locaux acceptés. Le changement vaut tout de suite pour les machines du domaine."
    fun optionDAnnuaire(n: Identifiant, adresse: String) = "Mon annuaire local — ${n.texte} · $adresse"
    fun rangerTitre(titre: String) = "Ranger une machine dans « $titre »"
    const val rangerExplication = "Une machine n'est rangée que dans un domaine à la fois : la ranger ici la retire de l'autre."
    const val ranger = "Ranger"
    const val toutEstRange = "Toutes vos machines sont déjà rangées ici."
}

// ── Ce que la page montre et permet, en fonctions pures ──────────────────────

/** Le badge d'un domaine : « Propriétaire », « Administrer », ou les droits tenus — comme le Mac (`BadgeDeRole`). */
internal enum class Role { Proprietaire, Administrateur, Droits }

internal data class RoleDuDomaine(val role: Role, val texte: String) {
    companion object {
        fun de(domaine: Domaine, moi: Identifiant?): RoleDuDomaine = when {
            domaine.proprietaire == moi -> RoleDuDomaine(Role.Proprietaire, TextesPageDomaines.proprietaire)
            domaine.peut(Domaine.ADMINISTRER) -> RoleDuDomaine(Role.Administrateur, TextesPageDomaines.administrer)
            else -> RoleDuDomaine(Role.Droits, domaine.droits.joinToString(" · "))
        }
    }
}

/**
 * Les gestes qu'un domaine offre à ce compte — décidés comme le Mac, sur les droits que l'annuaire rend.
 *
 * Renommer : `administrer`. Changer l'hébergeur : le posséder ET pouvoir y ranger, et que ce ne soit pas le domaine
 * racine ([Domaine.seConfie]) — R, même à son propriétaire, ne se confie pas ; depuis que l'annuaire lui rend les quatre
 * droits (0.39.0), seul `"sorte":"racine"` le distingue. Ranger une machine : `rattacher`, tenu en propre
 * ([Domaine.admetUneMachine]) — ni la propriété ni `administrer` n'y suffisent, sans quoi le propriétaire du domaine
 * racine se verrait offrir « Ranger une machine ici… » sur un annuaire qui refuse encore d'y en ranger. Supprimer : le
 * posséder ; que ce soit le dernier, c'est l'annuaire qui le dit (`DernierDomaine`).
 */
internal data class GestesDuDomaine(val renommer: Boolean, val changerHebergeur: Boolean, val ranger: Boolean, val supprimer: Boolean) {
    companion object {
        fun de(domaine: Domaine, moi: Identifiant?): GestesDuDomaine {
            val aMoi = moi != null && domaine.proprietaire == moi
            return GestesDuDomaine(
                renommer = domaine.peut(Domaine.ADMINISTRER),
                changerHebergeur = aMoi && domaine.admetUneMachine && domaine.seConfie,
                ranger = domaine.admetUneMachine,
                supprimer = aMoi,
            )
        }
    }
}

/** On ne retire d'un domaine que ses propres machines : celles des autres y sont rangées par leur propriétaire. */
internal fun retirableDuDomaine(machine: MachineDuDomaine, moi: Identifiant?): Boolean = moi != null && machine.proprietaire == moi

/** La ligne d'une machine rangée : son identifiant, et à qui elle est. */
internal fun sousTitreDeMachineRangee(machine: MachineDuDomaine, moi: Identifiant?): String =
    "${machine.machine.texte} · ${if (machine.proprietaire == moi) TextesPageDomaines.aVous else machine.proprietaire.texte}"

/** Les machines qu'on peut ranger ici : les miennes, qui n'y sont pas déjà. */
internal fun machinesARanger(miennes: List<Machine>, detail: DetailDuDomaine): List<Machine> =
    miennes.filter { m -> detail.machines.none { it.machine == m.id } }

/** À qui confier un domaine : les annuaires locaux acceptés dont je suis titulaire. */
internal fun titulairesAcceptes(locaux: List<Inscription>): List<Inscription> =
    locaux.filter { it.estTitulaire && it.etat == EtatDInscription.Acceptee && it.annuaire != null }

/**
 * Ce que la ligne « Hébergé par » dit, ligne à ligne : qui sert, puis ses adresses — la règle de `ValeurHebergement`
 * du Mac. Un annuaire local se dit « Mon annuaire local » quand il est à moi, suivi de son `n-…` ; ses adresses sont
 * celles que ses membres ont déclarées. Les racines se disent chacune avec ses locateurs.
 */
internal fun lignesDHebergement(domaine: Domaine, hebergement: Hebergement, locaux: List<Inscription>): Pair<String, List<String>> {
    val n = domaine.hebergePar ?: return TextesPageDomaines.lesRacines to
        hebergement.serveurs.map { "${it.nom} — ${it.adresses.joinToString(" · ")}" }
    val qui = if (locaux.any { it.annuaire == n }) TextesPageDomaines.monAnnuaireLocal else TextesPageDomaines.lAnnuaireLocal
    return qui to listOf(n.texte) + hebergement.serveurs.flatMap { it.adresses }
}

// ── Les feuilles ─────────────────────────────────────────────────────────────

/** Les feuilles des domaines : créer, renommer, changer l'hébergeur, ranger une machine. */
private sealed interface FeuilleDomaine {
    data object Creer : FeuilleDomaine
    data class Nom(val domaine: Domaine) : FeuilleDomaine
    data class Hebergeur(val domaine: Domaine) : FeuilleDomaine
    data class Ranger(val detail: DetailDuDomaine) : FeuilleDomaine
}

/** La feuille ouverte ; [apres] relit la page quand l'annuaire a rangé le geste. */
@Composable
private fun FeuilleDuDomaine(feuille: FeuilleDomaine, onFermer: () -> Unit, apres: () -> Unit) {
    val session = LocalSession.current
    val geste = rememberGesteEnFeuille { apres(); onFermer() }
    when (feuille) {
        FeuilleDomaine.Creer -> {
            var alias by remember { mutableStateOf("") }
            FeuilleDeSaisie(
                titre = TextesPageDomaines.creerTitre, explication = TextesPageDomaines.creerExplication, action = TextesPageDomaines.creer,
                actionPermise = Saisies.creationDeDomaine(alias), enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
                valider = { geste.faire { session.annuaire.creerDomaine(alias.ifEmpty { null }) } },
            ) { ChampDAlias(alias, TextesDomaines.aliasFacultatif) { alias = it } }
        }
        is FeuilleDomaine.Nom -> {
            val domaine = feuille.domaine
            var alias by remember { mutableStateOf(domaine.alias ?: "") }
            val range = Saisies.aliasDeDomaine(alias, domaine.alias)
            FeuilleDeSaisie(
                titre = TextesPageDomaines.nomTitre, explication = TextesPageDomaines.nomExplication, action = TextesPageDomaines.enregistrer,
                actionPermise = range != null, enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
                valider = { range?.let { a -> geste.faire { session.annuaire.definirAliasDomaine(domaine.id, a) } } },
                gauche = if (domaine.alias != null) ({
                    BoutonDestructif(TextesPageDomaines.retirerLAlias, actif = !geste.enCours) {
                        geste.faire { session.annuaire.definirAliasDomaine(domaine.id, null) }
                    }
                }) else null,
            ) {
                ChampDAlias(alias, TextesPageDomaines.alias) { alias = it }
                LigneAGeste(TextesPageDomaines.domaine) { TexteFixe(domaine.id.texte, secondaire = true) }
            }
        }
        is FeuilleDomaine.Hebergeur -> {
            val domaine = feuille.domaine
            // Les annuaires à qui confier ne se lisent qu'à l'ouverture : la liste n'a pas à les demander pour rien.
            val locaux = rememberChargement { session.annuaire.annuairesLocaux() }
            val titulaires = titulairesAcceptes(locaux.valeur.orEmpty())
            var choisi by remember { mutableStateOf(domaine.hebergePar) }
            FeuilleDeSaisie(
                titre = TextesPageDomaines.hebergementTitre(domaine.affiche), explication = TextesPageDomaines.hebergementExplication,
                action = TextesPageDomaines.enregistrer, actionPermise = choisi != domaine.hebergePar, enCours = geste.enCours,
                erreur = geste.erreur ?: locaux.erreur, onFermer = onFermer,
                valider = { geste.faire { session.annuaire.confier(domaine.id, choisi) } },
            ) {
                Choix(TextesPageDomaines.lesRacines, choisi == null) { choisi = null }
                titulaires.forEach { local ->
                    val n = local.annuaire!!
                    Choix(TextesPageDomaines.optionDAnnuaire(n, local.adresse), choisi == n) { choisi = n }
                }
                // Ce qu'on ne peut pas faire se dit : sans annuaire local accepté, seules les racines servent.
                if (locaux.valeur != null && titulaires.isEmpty()) TexteAbsent(TextesDomaines.aucunAnnuaire)
            }
        }
        is FeuilleDomaine.Ranger -> {
            val detail = feuille.detail
            val machines = rememberChargement { session.annuaire.machines() }
            val candidates = machines.valeur?.let { machinesARanger(it, detail) }
            var choisie by remember { mutableStateOf<Identifiant?>(null) }
            FeuilleDeSaisie(
                titre = TextesPageDomaines.rangerTitre(detail.domaine.affiche), explication = TextesPageDomaines.rangerExplication,
                action = TextesPageDomaines.ranger, actionPermise = choisie != null, enCours = geste.enCours,
                erreur = geste.erreur ?: machines.erreur, onFermer = onFermer,
                valider = { choisie?.let { m -> geste.faire { session.annuaire.rattacher(m, detail.domaine.id) } } },
            ) {
                if (candidates?.isEmpty() == true) TexteAbsent(TextesPageDomaines.toutEstRange)
                candidates.orEmpty().forEach { m -> Choix(m.affichee, choisie == m.id) { choisie = m.id } }
            }
        }
    }
}

/** Le champ d'un alias de domaine : rouge dès qu'il serait refusé. */
@Composable
private fun ChampDAlias(alias: String, libelle: String, surChangement: (String) -> Unit) {
    OutlinedTextField(
        alias, surChangement, label = { Text(libelle) }, singleLine = true,
        isError = alias.isNotEmpty() && Alias.pourDomaine(alias) == null, modifier = Modifier.fillMaxWidth(),
    )
}

/** Une option d'un choix unique, dans une feuille : toute la ligne se touche. */
@Composable
internal fun Choix(texte: String, choisi: Boolean, surChoix: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = surChoix), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = choisi, onClick = surChoix)
        Text(texte, style = MaterialTheme.typography.bodyLarge)
    }
}

// ── Les morceaux des deux pages ──────────────────────────────────────────────

@Composable
private fun BadgeDeRole(domaine: Domaine, moi: Identifiant?) {
    val role = RoleDuDomaine.de(domaine, moi)
    Badge(role.texte, when (role.role) {
        Role.Proprietaire -> Couleurs.accent
        Role.Administrateur -> Couleurs.attention
        Role.Droits -> Couleurs.parti
    })
}

/** Qui sert le domaine, et chacune de ses adresses sur sa ligne. */
@Composable
private fun ValeurHebergement(domaine: Domaine, hebergement: Hebergement, locaux: List<Inscription>) {
    val (qui, lignes) = lignesDHebergement(domaine, hebergement, locaux)
    Text(qui, style = MaterialTheme.typography.bodyMedium)
    lignes.forEach { TexteFixe(it, secondaire = true) }
}

// ── Domaines ─────────────────────────────────────────────────────────────────

/** Mes domaines, et ceux où l'un de mes groupes tient un droit : une tuile chacun, entière. */
@Composable
fun DomainesEcran(nav: NavController) {
    val session = LocalSession.current
    val chargement = rememberChargement { session.annuaire.domaines() }
    // Qui sert un domaine confié à un annuaire local : les adresses déclarées de ses membres — demandées seulement s'il y en a un.
    val locaux = rememberChargement {
        if (session.annuaire.domaines().any { it.hebergePar != null }) runCatching { session.annuaire.annuairesLocaux() }.getOrDefault(emptyList()) else emptyList()
    }
    val racines = session.choix?.racines.orEmpty()
    val moi = session.compte?.identifiant
    var feuille by remember { mutableStateOf<FeuilleDomaine?>(null) }

    Scaffold(
        topBar = { Barre(TextesDomaines.domaines, nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { feuille = FeuilleDomaine.Creer },
                icon = { Icon(Icones.plus, null) },
                text = { Text(TextesPageDomaines.creerUnDomaine) },
            )
        },
    ) { marges ->
        // Le bas de la liste laisse passer le bouton flottant.
        LazyColumn(Modifier.fillMaxSize().padding(marges), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Aide(TextesPageDomaines.introduction) }
            item { Erreur(chargement.erreur) }
            if (chargement.valeur?.isEmpty() == true) item { EtatVide(TextesPageDomaines.aucunDomaine, TextesPageDomaines.aucunDomaineAide) }
            items(chargement.valeur.orEmpty(), key = { it.id.texte }) { domaine ->
                val gestes = GestesDuDomaine.de(domaine, moi)
                Tuile {
                    TeteDeTuile(domaine.affiche) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (domaine.racine) Badge(TextesPageDomaines.domaineRacine, Couleurs.parti)
                            BadgeDeRole(domaine, moi)
                        }
                    }
                    LigneAGeste(TextesPageDomaines.identifiant, geste = { BoutonCopier(domaine.id.texte) }) { TexteFixe(domaine.id.texte) }
                    LigneAGeste(
                        TextesPageDomaines.hebergePar,
                        geste = if (gestes.changerHebergeur) ({ BoutonDeGeste(TextesPageDomaines.changer) { feuille = FeuilleDomaine.Hebergeur(domaine) } }) else null,
                    ) { ValeurHebergement(domaine, Hebergement.de(domaine, racines, locaux.valeur.orEmpty()), locaux.valeur.orEmpty()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (gestes.renommer) BoutonDeGeste(TextesPageDomaines.renommer) { feuille = FeuilleDomaine.Nom(domaine) }
                        Button(onClick = { nav.navigate(Routes.domaine(domaine.id)) }) { Text(TextesPageDomaines.ouvrir) }
                    }
                }
            }
        }
    }

    feuille?.let { FeuilleDuDomaine(it, onFermer = { feuille = null }, apres = { chargement.recharger(); locaux.recharger() }) }
}

/** Un domaine : ce qu'on en lit, en une tuile, avec les gestes en regard ; ses machines ; sa suppression, en bas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomaineEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.domaine(id) }
    val detail = chargement.valeur
    val moi = session.compte?.identifiant
    // Les annuaires locaux : leurs adresses disent qui sert ce domaine, et s'il est « Mon annuaire local ».
    val locaux = rememberChargement { runCatching { session.annuaire.annuairesLocaux() }.getOrDefault(emptyList()) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var feuille by remember { mutableStateOf<FeuilleDomaine?>(null) }
    var aRetirer by remember { mutableStateOf<MachineDuDomaine?>(null) }
    var supprimer by remember { mutableStateOf(false) }

    fun agir(bloc: suspend () -> Unit, apres: () -> Unit = { chargement.recharger() }) = portee.launch {
        enCours = true
        runCatching { bloc() }.onSuccess { erreur = null; apres() }.onFailure { erreur = it.messageAnnuaire }
        enCours = false
    }

    Scaffold(topBar = { Barre(detail?.domaine?.affiche ?: "", nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Erreur(chargement.erreur ?: erreur) }
            if (detail == null) return@LazyColumn
            val domaine = detail.domaine
            val gestes = GestesDuDomaine.de(domaine, moi)
            item {
                Tuile {
                    LigneAGeste(
                        TextesPageDomaines.nom,
                        geste = if (gestes.renommer) ({ BoutonDeGeste(TextesPageDomaines.modifier) { feuille = FeuilleDomaine.Nom(domaine) } }) else null,
                    ) {
                        val alias = domaine.alias
                        if (alias != null) Text(alias, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        else TexteAbsent(TextesDomaines.pasDAlias)
                    }
                    LigneAGeste(TextesPageDomaines.identifiant, geste = { BoutonCopier(domaine.id.texte) }) { TexteFixe(domaine.id.texte) }
                    LigneAGeste(TextesDomaines.proprietaire, geste = { BoutonCopier(domaine.proprietaire.texte) }) {
                        TexteFixe(domaine.proprietaire.texte)
                        if (domaine.proprietaire == moi) TexteAbsent("(${TextesDomaines.vous})")
                    }
                    LigneAGeste(
                        TextesPageDomaines.hebergePar,
                        geste = if (gestes.changerHebergeur) ({ BoutonDeGeste(TextesPageDomaines.changer) { feuille = FeuilleDomaine.Hebergeur(domaine) } }) else null,
                    ) { ValeurHebergement(domaine, Hebergement.de(domaine, session.choix?.racines.orEmpty(), locaux.valeur.orEmpty()), locaux.valeur.orEmpty()) }
                    LigneAGeste(TextesPageDomaines.mesDroits) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            domaine.droits.forEach { Badge(it, Couleurs.accent) }
                        }
                        // Les droits ne se changent pas ici : ce sont les groupes qui les donnent.
                        NoteDeTuile(TextesPageDomaines.tenusParVosGroupes)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(TextesDomaines.machinesRangees, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (gestes.ranger) BoutonDeGeste(TextesPageDomaines.rangerIci) { feuille = FeuilleDomaine.Ranger(detail) }
                }
            }
            item {
                Tuile {
                    if (detail.machines.isEmpty()) TexteAbsent(TextesDomaines.aucuneMachine)
                    detail.machines.forEachIndexed { indice, machine ->
                        if (indice > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(machine.affichee, style = MaterialTheme.typography.bodyLarge)
                                TexteFixe(sousTitreDeMachineRangee(machine, moi), secondaire = true)
                            }
                            if (retirableDuDomaine(machine, moi)) BoutonDeGeste(TextesPageDomaines.retirerDuDomaine) { aRetirer = machine }
                        }
                    }
                }
            }
            if (gestes.supprimer) item {
                PiedDestructif(
                    TextesPageDomaines.suppression, "${TextesDomaines.supprimer}…",
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp), actif = !enCours,
                ) { supprimer = true }
            }
        }
    }

    feuille?.let { FeuilleDuDomaine(it, onFermer = { feuille = null }, apres = { chargement.recharger(); locaux.recharger() }) }
    aRetirer?.let { machine ->
        Confirmation(
            titre = TextesPageDomaines.confirmerRetraitDeLaMachine, texte = TextesPageDomaines.retraitDeLaMachine,
            action = TextesPageDomaines.retirerLaMachine(machine.affichee),
            onAnnuler = { aRetirer = null },
            onConfirmer = { aRetirer = null; agir({ session.annuaire.rattacher(machine.machine, null) }) },
        )
    }
    if (supprimer) {
        Confirmation(
            titre = TextesDomaines.supprimer, texte = TextesDomaines.confirmerSuppression, action = TextesDomaines.supprimer,
            onAnnuler = { supprimer = false },
            onConfirmer = { supprimer = false; agir({ session.annuaire.supprimerDomaine(id) }, apres = { nav.popBackStack() }) },
        )
    }
}
