package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.BoutonCopier
import org.airdesktop.servicelocator.composants.BoutonDeGeste
import org.airdesktop.servicelocator.composants.BoutonDestructif
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.FeuilleDeSaisie
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneAGeste
import org.airdesktop.servicelocator.composants.LigneEcho
import org.airdesktop.servicelocator.composants.Pastille
import org.airdesktop.servicelocator.composants.PiedDestructif
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.TexteAbsent
import org.airdesktop.servicelocator.composants.TexteFixe
import org.airdesktop.servicelocator.composants.Tuile
import org.airdesktop.servicelocator.composants.couleur
import org.airdesktop.servicelocator.composants.detailEtat
import org.airdesktop.servicelocator.composants.libelleEtat
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.composants.rememberGesteEnFeuille
import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.modele.Versions
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire
import java.time.Instant

// ── La fiche, à la lettre le Mac ─────────────────────────────────────────────

/** Les mots de la fiche d'une machine — **recopiés de l'application Mac** (`MachineFenetreVue.swift`, PR iOS #36). */
internal object TextesMachine {
    const val identifiant = "Identifiant public"
    const val nomDHote = "Nom d'hôte"
    const val alias = "Alias"
    const val capacites = "Capacités"
    const val cle = "Clé"
    const val aucun = "aucun"
    const val aucune = "aucune"
    const val modifier = "Modifier…"
    const val choisir = "Choisir…"
    const val changer = "Changer…"
    const val enregistrer = "Enregistrer"
    const val retirerLAlias = "Retirer l'alias"

    const val annonce = "annonce — ses daemons peuvent annoncer leurs ports"
    const val lecture = "lecture — elle peut demander où joindre un service"
    const val annonceCase = "Annonce — ses daemons peuvent annoncer leurs ports"
    const val lectureCase = "Lecture — elle peut demander où joindre un service"

    const val nomTitre = "Modifier le nom d'hôte"
    const val aliasTitre = "Alias de la machine"
    const val capacitesTitre = "Capacités de la machine"
    const val capacitesExplication = "Ce que la machine a le droit de faire auprès de l'annuaire."
    /** Ce que la page des capacités disait déjà : ce que retirer une capacité ferme, et ce qu'il ne ferme pas. */
    const val capacitesEffet =
        "Retirer l'annonce ferme les connexions de la machine et fait tomber ses baux. Retirer la lecture ne ferme rien : sa prochaine demande sera refusée."
    fun rangeSous(forme: String) = "Sera rangé « $forme »."

    const val revoquer = "Révoquer la clé…"
    const val revoquerLaCle = "Révoquer la clé"
    fun confirmerRevocation(titre: String) = "Révoquer la clé de « $titre » ?"
    const val revocation =
        "Effet immédiat : ses connexions sont fermées, ses baux tombent. La machine reste — son nom, ses capacités, ses services — et un nouveau code la ré-enrôle."
    const val gesteRevocation =
        "Révoquer la clé ferme ses connexions et fait tomber ses baux, tout de suite. La machine reste — son nom, ses capacités, ses services — et un nouveau code la ré-enrôle."
    const val codeDEnrolement = "Code d'enrôlement"
    const val gesteCode = "Le code se tape sur la machine, qui y génère sa clé. Il est à usage unique et vaut dix minutes."

    /** Ce que la ligne « Clé » dit, comme le Mac : l'état, sa date quand cet appareil la sait, et ce qui suit. */
    fun cle(cle: Machine.Cle, maintenant: Instant = Instant.now()): String = when (cle) {
        is Machine.Cle.Enrolee -> (cle.le?.let { "enrôlée le ${Formats.jour(it)}" } ?: "enrôlée") + " — Ed25519, générée sur la machine"
        is Machine.Cle.Attendue -> {
            val code = cle.code
            when {
                code == null -> "attendue — pas de code en cours"
                code.estValide(maintenant) -> "attendue — un code est valable"
                else -> "attendue — le code a expiré"
            }
        }
        is Machine.Cle.Revoquee -> "révoquée le ${Formats.jour(cle.le)} — un nouveau code la ré-enrôle"
    }

    /** Les capacités en toutes lettres, une par ligne ; aucune, la liste est vide et la ligne dit « aucune ». */
    fun capacites(capacites: Set<Capacite>): List<String> = buildList {
        if (Capacite.ANNONCE in capacites) add(annonce)
        if (Capacite.LECTURE in capacites) add(lecture)
    }
}

/** Les feuilles de la fiche : le nom d'hôte, l'alias, le domaine, les capacités. */
private enum class FeuilleMachine { Nom, Alias, Domaine, Capacites }

/**
 * Une machine : ce qu'on en lit, en une tuile, chaque donnée qui se change avec son bouton en regard et sa feuille ;
 * ses services ; en bas, à part, le geste sur la clé — la révoquer, en rouge, ou aller au code qui l'enrôle.
 */
@Composable
fun MachineEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.machines().firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable }
    val machine = chargement.valeur
    var erreur by remember { mutableStateOf<String?>(null) }
    var feuille by remember { mutableStateOf<FeuilleMachine?>(null) }
    // **LE CHAMP ALIAS N'APPARAÎT QUE SUR UN ANNUAIRE QUI LE RANGE** (≥ 0.26.0), comme sur iOS : une version illisible
    // ne l'affiche pas. Un `404`/`405` qui arriverait quand même dit « racine trop ancienne » (AnnuaireReel).
    var aliasAdmis by remember { mutableStateOf(false) }
    LaunchedEffect(session.annuaire) {
        aliasAdmis = Alias.aliasDeMachineAdmis(runCatching { session.annuaire.annonce()?.version }.getOrNull())
    }
    var confirmerRevocation by remember { mutableStateOf(false) }
    var revocationEnCours by remember { mutableStateOf(false) }
    // **LE DOMAINE D'UNE MACHINE NE VOYAGE PAS AVEC ELLE** : `GET /v1/machines` ne le rend pas. On le retrouve en lisant
    // les domaines où l'on voit des machines — peu nombreux —, et seulement sur un annuaire qui les sert (≥ 0.23.0).
    var domaines by remember { mutableStateOf<List<Domaine>?>(null) }
    var rangeeDans by remember { mutableStateOf<Identifiant?>(null) }
    var tourDomaine by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.annuaire, tourDomaine) {
        val version = runCatching { session.annuaire.annonce()?.version }.getOrNull()
        if (!Versions.auMoins(version, Versions.DOMAINES)) { domaines = null; return@LaunchedEffect }
        val lus = runCatching { session.annuaire.domaines() }.getOrNull() ?: return@LaunchedEffect
        domaines = lus
        rangeeDans = lus.firstOrNull { d -> runCatching { session.annuaire.domaine(d.id).machines.any { it.machine == id } }.getOrDefault(false) }?.id
    }

    Scaffold(topBar = { Barre(machine?.affichee ?: "", nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Erreur(chargement.erreur ?: erreur) }
            if (machine == null) return@LazyColumn
            item {
                Tuile {
                    LigneAGeste(TextesMachine.identifiant, geste = { BoutonCopier(machine.id.texte) }) { TexteFixe(machine.id.texte) }
                    LigneAGeste(TextesMachine.nomDHote, geste = { BoutonDeGeste(TextesMachine.modifier) { feuille = FeuilleMachine.Nom } }) {
                        TexteFixe(machine.nom)
                    }
                    if (aliasAdmis) {
                        val alias = machine.alias
                        LigneAGeste(
                            TextesMachine.alias,
                            geste = { BoutonDeGeste(if (alias == null) TextesMachine.choisir else TextesMachine.modifier) { feuille = FeuilleMachine.Alias } },
                        ) { if (alias != null) Text(alias, style = MaterialTheme.typography.bodyMedium) else TexteAbsent(TextesMachine.aucun) }
                    }
                    domaines?.let { lus ->
                        LigneAGeste(TextesDomaines.domaineDeLaMachine, geste = { BoutonDeGeste(TextesMachine.changer) { feuille = FeuilleMachine.Domaine } }) {
                            val dans = lus.firstOrNull { it.id == rangeeDans }
                            if (dans != null) Text(dans.affiche, style = MaterialTheme.typography.bodyMedium) else TexteAbsent(TextesDomaines.aucun)
                        }
                    }
                    LigneAGeste(TextesMachine.capacites, geste = { BoutonDeGeste(TextesMachine.modifier) { feuille = FeuilleMachine.Capacites } }) {
                        val lignes = TextesMachine.capacites(machine.capacites)
                        if (lignes.isEmpty()) TexteAbsent(TextesMachine.aucune)
                        lignes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    LigneAGeste(TextesMachine.cle) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pastille(couleurDeLaCle(machine.cle), 8)
                            Text(TextesMachine.cle(machine.cle), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    LigneEcho(machine.echo)
                }
            }
            if (Capacite.ANNONCE in machine.capacites) {
                item { SousTitre("Services") }
                if (machine.services.isEmpty()) item { Aide("Aucun service annoncé pour l'instant.") }
                items(machine.services, key = { it.id.texte }) { LigneService(it, Modifier.clickable { nav.navigate(Routes.service(machine.id, it.id)) }) }
                item { Aide("« Joignable » veut dire : l'annuaire a lui-même ouvert une connexion vers ce port, à cette date. Un point d'écoute UDP ne se sonde pas.") }
            }
            item {
                val pied = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                when (machine.cle) {
                    is Machine.Cle.Enrolee -> PiedDestructif(TextesMachine.gesteRevocation, TextesMachine.revoquer, pied, actif = !revocationEnCours) {
                        confirmerRevocation = true
                    }
                    // Sans clé, le geste du bas ne détruit rien : il mène au code. Pas de rouge.
                    is Machine.Cle.Attendue, is Machine.Cle.Revoquee -> Column(pied, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(Modifier.padding(bottom = 4.dp))
                        Text(TextesMachine.gesteCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { nav.navigate(Routes.code(machine.id)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icones.terminal, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(TextesMachine.codeDEnrolement)
                        }
                    }
                }
            }
        }
    }

    if (machine != null) when (feuille) {
        FeuilleMachine.Nom -> FeuilleDuNom(machine, onFermer = { feuille = null }, apres = { chargement.recharger() })
        FeuilleMachine.Alias -> FeuilleDeLAlias(machine, onFermer = { feuille = null }, apres = { chargement.recharger() })
        FeuilleMachine.Capacites -> FeuilleDesCapacites(machine, onFermer = { feuille = null }, apres = { chargement.recharger() })
        FeuilleMachine.Domaine -> FeuilleDuDomaineDeLaMachine(machine, domaines.orEmpty(), rangeeDans, onFermer = { feuille = null }, apres = { tourDomaine++ })
        null -> Unit
    }

    if (confirmerRevocation && machine != null) {
        Confirmation(
            titre = TextesMachine.confirmerRevocation(machine.affichee), texte = TextesMachine.revocation, action = TextesMachine.revoquerLaCle,
            onAnnuler = { confirmerRevocation = false },
            onConfirmer = {
                confirmerRevocation = false
                revocationEnCours = true
                portee.launch {
                    runCatching { session.annuaire.revoquerCle(id) }
                        .onSuccess { erreur = null; chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    revocationEnCours = false
                }
            },
        )
    }
}

/** La couleur de la clé, celle du Mac : enrôlée, verte ; attendue, grise ; révoquée, orange. */
private fun couleurDeLaCle(cle: Machine.Cle) = when (cle) {
    is Machine.Cle.Enrolee -> Couleurs.joignable
    is Machine.Cle.Attendue -> Couleurs.parti
    is Machine.Cle.Revoquee -> Couleurs.attention
}

@Composable
private fun FeuilleDuNom(machine: Machine, onFermer: () -> Unit, apres: () -> Unit) {
    val session = LocalSession.current
    val geste = rememberGesteEnFeuille { apres(); onFermer() }
    var nom by remember { mutableStateOf(machine.nom) }
    val aEnvoyer = Saisies.nomDeMachine(nom, machine.nom)
    FeuilleDeSaisie(
        titre = TextesMachine.nomTitre, explication = TEXTE_NOM_D_HOTE, action = TextesMachine.enregistrer,
        actionPermise = aEnvoyer != null, enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
        valider = { aEnvoyer?.let { n -> geste.faire { session.annuaire.modifierMachine(machine.id, nom = n) } } },
    ) {
        val forme = Machine.nomDHote(nom)
        OutlinedTextField(
            nom, { nom = it }, label = { Text(TextesMachine.nomDHote) }, singleLine = true,
            isError = nom.isNotEmpty() && forme == null, modifier = Modifier.fillMaxWidth(),
            supportingText = if (forme != null && forme != nom) ({ Text(TextesMachine.rangeSous(forme)) }) else null,
        )
    }
}

@Composable
private fun FeuilleDeLAlias(machine: Machine, onFermer: () -> Unit, apres: () -> Unit) {
    val session = LocalSession.current
    val geste = rememberGesteEnFeuille { apres(); onFermer() }
    var alias by remember { mutableStateOf(machine.alias ?: "") }
    val aEnvoyer = Saisies.aliasDeMachine(alias, machine.alias)
    FeuilleDeSaisie(
        titre = TextesMachine.aliasTitre, explication = TEXTE_ALIAS_DE_MACHINE, action = TextesMachine.enregistrer,
        actionPermise = aEnvoyer != null, enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
        valider = { aEnvoyer?.let { a -> geste.faire { session.annuaire.definirAliasMachine(machine.id, a) } } },
        gauche = if (machine.alias != null) ({
            BoutonDestructif(TextesMachine.retirerLAlias, actif = !geste.enCours) { geste.faire { session.annuaire.definirAliasMachine(machine.id, null) } }
        }) else null,
    ) {
        OutlinedTextField(
            alias, { alias = it }, label = { Text(TextesMachine.alias) }, singleLine = true,
            isError = alias.isNotEmpty() && Alias.pourMachine(alias) == null, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeuilleDesCapacites(machine: Machine, onFermer: () -> Unit, apres: () -> Unit) {
    val session = LocalSession.current
    val geste = rememberGesteEnFeuille { apres(); onFermer() }
    var choisies by remember { mutableStateOf(machine.capacites) }
    val aEnvoyer = Saisies.capacites(choisies, machine.capacites)
    FeuilleDeSaisie(
        titre = TextesMachine.capacitesTitre, explication = TextesMachine.capacitesExplication, action = TextesMachine.enregistrer,
        actionPermise = aEnvoyer != null, enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
        valider = { aEnvoyer?.let { c -> geste.faire { session.annuaire.modifierMachine(machine.id, capacites = c) } } },
    ) {
        Interrupteur(TextesMachine.annonceCase, Capacite.ANNONCE in choisies) { choisies = if (it) choisies + Capacite.ANNONCE else choisies - Capacite.ANNONCE }
        Interrupteur(TextesMachine.lectureCase, Capacite.LECTURE in choisies) { choisies = if (it) choisies + Capacite.LECTURE else choisies - Capacite.LECTURE }
        Text(TextesMachine.capacitesEffet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Une capacité, et son interrupteur — le `Toggle` du Mac. Rien n'est envoyé avant « Enregistrer ». */
@Composable
private fun Interrupteur(texte: String, actif: Boolean, surChangement: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(texte, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = actif, onCheckedChange = surChangement)
    }
}

/** Ranger la machine dans un domaine, ou la retirer de celui où elle est. */
@Composable
private fun FeuilleDuDomaineDeLaMachine(machine: Machine, domaines: List<Domaine>, rangeeDans: Identifiant?, onFermer: () -> Unit, apres: () -> Unit) {
    val session = LocalSession.current
    val geste = rememberGesteEnFeuille { apres(); onFermer() }
    // Les domaines où l'on tient `rattacher` en propre — le domaine racine seulement quand l'annuaire l'y rend
    // (0.39.0) ; la retirer, si elle est rangée.
    val options = domaines.filter { it.admetUneMachine }
    var choisi by remember { mutableStateOf(rangeeDans) }
    FeuilleDeSaisie(
        titre = TextesDomaines.ranger, explication = TextesPageDomaines.rangerExplication, action = TextesMachine.enregistrer,
        actionPermise = choisi != rangeeDans, enCours = geste.enCours, erreur = geste.erreur, onFermer = onFermer,
        valider = { geste.faire { session.annuaire.rattacher(machine.id, choisi) } },
    ) {
        options.forEach { d -> Choix(d.affiche, choisi == d.id) { choisi = d.id } }
        if (rangeeDans != null) Choix(TextesDomaines.retirerDuDomaine, choisi == null) { choisi = null }
        if (options.isEmpty()) TexteAbsent(TextesDomaines.aucun)
        // Le domaine choisi est à un autre compte : ce que le rangement ouvre se dit avant « Enregistrer ».
        if (choisi != rangeeDans) {
            avertissementDeRangement(options.firstOrNull { it.id == choisi }, session.compte?.identifiant)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Couleurs.attention)
            }
        }
    }
}

@Composable
fun LigneService(service: Service, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        leadingContent = { Pastille(service.couleur, 8) },
        headlineContent = {
            Text(service.nom, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                // Sans adresse, il n'y a pas de point à dire : la ligne ne s'en invente pas une vide.
                if (service.points.isNotEmpty()) Text(service.pointsTexte)
                if (service.oscille) Text("Deux daemons de ce nom se chassent l'un l'autre.", style = MaterialTheme.typography.bodySmall, color = Couleurs.attention)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(130.dp)) {
                Text(service.libelleEtat, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                Text(service.detailEtat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        },
    )
}

/** Ce que dit l'aide de l'alias d'une machine. */
internal const val TEXTE_ALIAS_DE_MACHINE =
    "Texte libre, pour vous : un nom complet, avec espaces et accents si vous voulez. Plusieurs machines peuvent porter le même."
