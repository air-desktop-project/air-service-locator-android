package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneIdentifiant
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.CodeDInscription
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription

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
    /** `racine` : l'adresse de la racine à laquelle l'application parle ; `<racine.pem>` et `<clé>` restent littéraux. */
    fun commande(code: String, racine: String) = "asl-server --register $code --directory $racine --ca <racine.pem> --identity-key <clé>"
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

// ── Domaines ─────────────────────────────────────────────────────────────────

/** Mes domaines, et ceux où l'un de mes groupes tient un droit. */
@Composable
fun DomainesEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.domaines() }
    var creer by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { Barre(TextesDomaines.domaines, nav) { TextButton(onClick = { creer = true }) { Text(TextesDomaines.creer) } } },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            items(chargement.valeur.orEmpty(), key = { it.id.texte }) { domaine ->
                ListItem(
                    headlineContent = { Text(domaine.affiche) },
                    supportingContent = {
                        Column {
                            if (domaine.alias != null) Text(domaine.id.texte, fontFamily = FontFamily.Monospace)
                            Text(libelleHebergeur(domaine))
                        }
                    },
                    trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.navigate(Routes.domaine(domaine.id)) },
                )
            }
            if (chargement.valeur?.isEmpty() == true) item { Aide(TextesDomaines.aucunDomaine) }
        }
    }

    if (creer) {
        DialogueDAlias(
            titre = TextesDomaines.creer,
            initial = "",
            facultatif = true,
            onAnnuler = { creer = false },
            onValider = { alias ->
                creer = false
                portee.launch {
                    runCatching { session.annuaire.creerDomaine(alias) }
                        .onSuccess { chargement.recharger(); erreur = null }
                        .onFailure { erreur = it.messageAnnuaire }
                }
            },
        )
    }
}

/** Un domaine : son alias, ce qui l'héberge, les machines qu'on y voit. */
@Composable
fun DomaineEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.domaine(id) }
    val detail = chargement.valeur
    val moi = session.compte?.identifiant
    // Les annuaires locaux acceptés dont je suis titulaire : ceux à qui je peux confier ce domaine.
    val mesAnnuaires = rememberChargement {
        runCatching { session.annuaire.annuairesLocaux() }.getOrDefault(emptyList())
            .filter { it.estTitulaire && it.etat == EtatDInscription.Acceptee }
    }
    var erreur by remember { mutableStateOf<String?>(null) }
    var nommer by remember { mutableStateOf(false) }
    var choisirHebergeur by remember { mutableStateOf(false) }
    var supprimer by remember { mutableStateOf(false) }

    fun agir(bloc: suspend () -> Unit, apres: () -> Unit = { chargement.recharger() }) = portee.launch {
        runCatching { bloc() }.onSuccess { erreur = null; apres() }.onFailure { erreur = it.messageAnnuaire }
    }

    Scaffold(topBar = { Barre(detail?.domaine?.affiche ?: "", nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            if (detail == null) return@LazyColumn
            val domaine = detail.domaine
            val proprietaire = domaine.proprietaire == moi
            item { SousTitre(TextesDomaines.domaineDeLaMachine) }
            item { LigneIdentifiant("Identifiant", domaine.id) }
            item {
                ListItem(
                    headlineContent = { Text("Alias") },
                    supportingContent = { Text(domaine.alias ?: TextesDomaines.aucun) },
                    trailingContent = if (domaine.peut(Domaine.ADMINISTRER)) ({ Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                    modifier = if (domaine.peut(Domaine.ADMINISTRER)) Modifier.clickable { nommer = true } else Modifier,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(libelleHebergeur(domaine)) },
                    supportingContent = if (proprietaire) ({ Text(if (domaine.hebergePar == null) TextesDomaines.confier else TextesDomaines.rendreAuxRacines) }) else null,
                    trailingContent = if (proprietaire) ({ Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                    modifier = if (proprietaire) Modifier.clickable { choisirHebergeur = true } else Modifier,
                )
            }
            item { SousTitre(TextesDomaines.machinesRangees) }
            if (detail.machines.isEmpty()) item { Aide(TextesDomaines.aucuneMachine) }
            items(detail.machines, key = { it.machine.texte }) { machine ->
                ListItem(
                    headlineContent = { Text(machine.affichee) },
                    supportingContent = { Text(machine.machine.texte, fontFamily = FontFamily.Monospace) },
                )
            }
            if (proprietaire) {
                item {
                    TextButton(onClick = { supprimer = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(TextesDomaines.supprimer, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    val domaine = detail?.domaine ?: return
    if (nommer) {
        DialogueDAlias(
            titre = "Alias du domaine",
            initial = domaine.alias ?: "",
            facultatif = false,
            retirable = domaine.alias != null,
            onAnnuler = { nommer = false },
            onValider = { alias -> nommer = false; agir({ session.annuaire.definirAliasDomaine(id, alias) }) },
        )
    }
    if (choisirHebergeur) {
        val options: List<Identifiant?> = listOf<Identifiant?>(null) + mesAnnuaires.valeur.orEmpty().mapNotNull { it.annuaire }
        var choisi by remember { mutableStateOf(domaine.hebergePar) }
        AlertDialog(
            onDismissRequest = { choisirHebergeur = false },
            title = { Text(TextesDomaines.confier) },
            text = {
                Column {
                    options.forEach { option ->
                        ListItem(
                            leadingContent = { RadioButton(selected = choisi == option, onClick = { choisi = option }) },
                            headlineContent = { Text(option?.let { TextesDomaines.confier + " — " + it.texte } ?: TextesDomaines.rendreAuxRacines) },
                            modifier = Modifier.clickable { choisi = option },
                        )
                    }
                    if (options.size == 1) Aide(TextesDomaines.aucunAnnuaire)
                }
            },
            confirmButton = {
                TextButton(enabled = choisi != domaine.hebergePar, onClick = {
                    choisirHebergeur = false
                    agir({ session.annuaire.confier(id, choisi) })
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { choisirHebergeur = false }) { Text("Annuler") } },
        )
    }
    if (supprimer) {
        AlertDialog(
            onDismissRequest = { supprimer = false },
            title = { Text(TextesDomaines.supprimer) },
            text = { Text(TextesDomaines.confirmerSuppression) },
            confirmButton = {
                TextButton(onClick = {
                    supprimer = false
                    agir({ session.annuaire.supprimerDomaine(id) }, apres = { nav.popBackStack() })
                }) { Text(TextesDomaines.supprimer, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { supprimer = false }) { Text("Annuler") } },
        )
    }
}

/** Saisir un alias de domaine : UTF-8, NFC, sensible à la casse, 64 octets au plus. */
@Composable
private fun DialogueDAlias(
    titre: String,
    initial: String,
    facultatif: Boolean,
    retirable: Boolean = false,
    onAnnuler: () -> Unit,
    onValider: (String?) -> Unit,
) {
    var texte by remember { mutableStateOf(initial) }
    val range = Alias.pourDomaine(texte)
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = {
            Column {
                OutlinedTextField(
                    texte, { texte = it }, label = { Text(if (facultatif) TextesDomaines.aliasFacultatif else "Alias") }, singleLine = true,
                    isError = texte.isNotEmpty() && range == null, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (facultatif && texte.isEmpty()) || (range != null && range != initial),
                onClick = { onValider(if (texte.isEmpty()) null else range) },
            ) { Text(if (facultatif) TextesDomaines.creer else "Enregistrer") }
        },
        dismissButton = {
            if (retirable) TextButton(onClick = { onValider(null) }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onAnnuler) { Text("Annuler") }
        },
    )
}

// ── Mon annuaire local ───────────────────────────────────────────────────────

/** Déclarer son annuaire local, suivre son inscription, déclarer sa paire, le retirer. */
@Composable
fun AnnuaireLocalEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.annuairesLocaux() }
    val inscriptions = chargement.valeur.orEmpty()
    var erreur by remember { mutableStateOf<String?>(null) }
    // Déclarer : `null` = premier membre ; un `n-…` = second membre de cet annuaire.
    var declarer by remember { mutableStateOf<Pair<Boolean, Identifiant?>>(false to null) }
    var code by remember { mutableStateOf<CodeDInscription?>(null) }
    var retirer by remember { mutableStateOf<Inscription?>(null) }
    // La racine à laquelle l'application parle : c'est elle que la machine joindra pour s'inscrire.
    val racine = session.choix?.choisie?.adresse ?: "<racine>"

    Scaffold(
        topBar = { Barre(TextesDomaines.annuaireLocal, nav) { TextButton(onClick = { declarer = true to null }) { Text(TextesDomaines.declarer) } } },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            code?.let { c ->
                item { SousTitre(TextesDomaines.codeTitre) }
                item { Aide(TextesDomaines.codeAide) }
                item {
                    ListItem(
                        headlineContent = { Text(TextesDomaines.commande(c.code, racine), fontFamily = FontFamily.Monospace) },
                    )
                }
            }
            if (inscriptions.isEmpty() && chargement.valeur != null) item { Aide(TextesDomaines.aucunAnnuaire) }
            // Un annuaire par titulaire ; les déclarations qui attendent encore leur machine, à part.
            val parAnnuaire = inscriptions.groupBy { it.annuaire }
            parAnnuaire.forEach { (annuaire, membres) ->
                item { SousTitre(annuaire?.texte ?: TextesDomaines.codeTitre) }
                items(membres) { inscription ->
                    ListItem(
                        headlineContent = { Text(inscription.adresse, fontFamily = FontFamily.Monospace) },
                        supportingContent = {
                            Column {
                                Text(TextesDomaines.etat(inscription))
                                inscription.membre?.let { Text(it.texte, fontFamily = FontFamily.Monospace) }
                            }
                        },
                        trailingContent = if (inscription.membre != null && inscription.etat in setOf(EtatDInscription.EnAttente, EtatDInscription.Acceptee)) ({
                            TextButton(onClick = { retirer = inscription }) { Text(TextesDomaines.retirer, color = MaterialTheme.colorScheme.error) }
                        }) else null,
                    )
                }
                val titulaireAccepte = membres.any { it.estTitulaire && it.etat == EtatDInscription.Acceptee }
                val vivants = membres.count { it.membre != null && it.etat in setOf(EtatDInscription.EnAttente, EtatDInscription.Acceptee) }
                if (annuaire != null && titulaireAccepte && vivants < 2) item {
                    TextButton(onClick = { declarer = true to annuaire }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(TextesDomaines.secondMembre)
                    }
                }
            }
        }
    }

    if (declarer.first) {
        val second = declarer.second
        var adresse by remember { mutableStateOf("") }
        val forme = Alias.adresseDAnnuaire(adresse)
        AlertDialog(
            onDismissRequest = { declarer = false to null },
            title = { Text(if (second == null) TextesDomaines.declarer else TextesDomaines.secondMembre) },
            text = {
                Column {
                    OutlinedTextField(
                        adresse, { adresse = it }, label = { Text(TextesDomaines.adresse) }, singleLine = true,
                        isError = adresse.isNotEmpty() && forme == null, modifier = Modifier.fillMaxWidth(),
                    )
                    Aide(TextesDomaines.adresseAide)
                }
            },
            confirmButton = {
                TextButton(enabled = forme != null, onClick = {
                    declarer = false to null
                    portee.launch {
                        runCatching {
                            if (second == null) session.annuaire.declarerAnnuaire(forme!!) else session.annuaire.declarerSecondMembre(second, forme!!)
                        }.onSuccess { code = it; erreur = null; chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text(TextesDomaines.declarer) }
            },
            dismissButton = { TextButton(onClick = { declarer = false to null }) { Text("Annuler") } },
        )
    }
    retirer?.let { inscription ->
        val titulaire = inscription.estTitulaire
        AlertDialog(
            onDismissRequest = { retirer = null },
            title = { Text(TextesDomaines.retirer) },
            text = { Text(TextesDomaines.confirmerRetrait) },
            confirmButton = {
                TextButton(onClick = {
                    retirer = null
                    portee.launch {
                        runCatching {
                            val annuaire = inscription.annuaire ?: return@runCatching
                            if (titulaire) session.annuaire.retirerAnnuaire(annuaire) else session.annuaire.retirerMembre(annuaire, inscription.membre!!)
                        }.onSuccess { erreur = null; chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text(TextesDomaines.retirer, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { retirer = null }) { Text("Annuler") } },
        )
    }
}

// ── Administration des racines ───────────────────────────────────────────────

/** Les inscriptions qui attendent un administrateur des racines — cet écran n'existe que pour eux. */
@Composable
fun AdministrationDesRacinesEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.inscriptionsEnAttente() }
    var erreur by remember { mutableStateOf<String?>(null) }
    // La décision à confirmer : l'inscription, et accepter ou refuser.
    var decision by remember { mutableStateOf<Pair<Inscription, Boolean>?>(null) }

    Scaffold(topBar = { Barre(TextesDomaines.administration, nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            val liste = chargement.valeur
            if (chargement.valeur == null && chargement.erreur == null) return@LazyColumn
            if (liste.isNullOrEmpty()) item { Aide(TextesDomaines.aucuneInscription) }
            items(liste.orEmpty()) { inscription ->
                ListItem(
                    headlineContent = { Text(inscription.adresse, fontFamily = FontFamily.Monospace) },
                    supportingContent = {
                        Column {
                            inscription.membre?.let { Text(it.texte, fontFamily = FontFamily.Monospace) }
                            inscription.proprietaire?.let { Text(it.texte, fontFamily = FontFamily.Monospace) }
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { decision = inscription to true }) { Text(TextesDomaines.accepter) }
                            TextButton(onClick = { decision = inscription to false }) { Text(TextesDomaines.refuser, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                )
            }
        }
    }

    decision?.let { (inscription, accepte) ->
        AlertDialog(
            onDismissRequest = { decision = null },
            title = { Text(if (accepte) TextesDomaines.accepter else TextesDomaines.refuser) },
            text = {
                Text(
                    if (accepte) TextesDomaines.confirmerAcceptation(
                        inscription.membre?.texte ?: "?", inscription.adresse, inscription.proprietaire?.texte ?: "?",
                    ) else TextesDomaines.confirmerRefus,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    decision = null
                    val membre = inscription.membre ?: return@TextButton
                    portee.launch {
                        runCatching { session.annuaire.decider(membre, accepte) }
                            .onSuccess { erreur = null; chargement.recharger() }
                            .onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text(if (accepte) TextesDomaines.accepter else TextesDomaines.refuser, color = if (accepte) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { decision = null }) { Text("Annuler") } },
        )
    }
}
