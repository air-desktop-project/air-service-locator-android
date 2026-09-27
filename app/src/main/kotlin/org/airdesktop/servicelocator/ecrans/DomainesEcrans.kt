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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Les textes, dits une fois ────────────────────────────────────────────────

internal const val TEXTE_DOMAINES =
    "Un domaine rassemble des machines. Votre compte en a toujours au moins un ; il n'est pas dans le domaine, il le possède."
internal const val TEXTE_ALIAS_DE_DOMAINE =
    "Texte libre, visible de tous les comptes : n'importe qui peut chercher un domaine par son alias. Plusieurs domaines peuvent porter le même — c'est l'identifiant qui fait foi."
internal const val TEXTE_ANNUAIRE_LOCAL =
    "Un annuaire que vous faites tourner chez vous, sur l'une de vos machines, pour vos domaines. Les racines l'inscrivent après qu'un de leurs administrateurs l'a accepté."
internal const val TEXTE_CODE_D_INSCRIPTION =
    "À taper sur la machine, dans les vingt-quatre heures :"
internal const val TEXTE_ADMINISTRATION =
    "Les annuaires locaux qui demandent à être inscrits. Un administrateur suffit ; un refus l'emporte sur une acceptation, même venue d'une autre racine."

private val format = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())

/** Ce qu'on dit de ce qui héberge un domaine. */
internal fun libelleHebergeur(domaine: Domaine): String =
    domaine.hebergePar?.let { "Hébergé par l'annuaire local ${it.abrege}" } ?: "Hébergé par les racines"

/** Le mot d'un état d'inscription, tel qu'on le montre. */
internal fun libelleDInscription(inscription: Inscription): String = when (inscription.etat) {
    EtatDInscription.Attendue -> "Code en attente de la machine" + (inscription.expireA?.let { " — jusqu'au ${format.format(it)}" } ?: "")
    EtatDInscription.EnAttente -> "En attente d'un administrateur des racines"
    EtatDInscription.Acceptee -> "Inscrit"
    EtatDInscription.Refusee -> "Refusé"
    EtatDInscription.Retiree -> "Retiré"
    EtatDInscription.Inconnu -> inscription.motDeLEtat
}

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
        topBar = { Barre("Domaines", nav) { TextButton(onClick = { creer = true }) { Text("Créer") } } },
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
            item { Aide(TEXTE_DOMAINES) }
        }
    }

    if (creer) {
        DialogueDAlias(
            titre = "Nouveau domaine",
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
            item { SousTitre("Domaine") }
            item { LigneIdentifiant("Identifiant", domaine.id) }
            item {
                ListItem(
                    headlineContent = { Text("Alias") },
                    supportingContent = { Text(domaine.alias ?: "aucun") },
                    trailingContent = if (domaine.peut(Domaine.ADMINISTRER)) ({ Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                    modifier = if (domaine.peut(Domaine.ADMINISTRER)) Modifier.clickable { nommer = true } else Modifier,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Hébergement") },
                    supportingContent = { Text(libelleHebergeur(domaine)) },
                    trailingContent = if (proprietaire) ({ Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
                    modifier = if (proprietaire) Modifier.clickable { choisirHebergeur = true } else Modifier,
                )
            }
            item { SousTitre("Machines") }
            if (detail.machines.isEmpty()) item { Aide("Aucune machine rangée ici — on range une de ses machines depuis sa fiche.") }
            items(detail.machines, key = { it.machine.texte }) { machine ->
                ListItem(
                    headlineContent = { Text(machine.affichee) },
                    supportingContent = { Text(machine.machine.texte, fontFamily = FontFamily.Monospace) },
                )
            }
            if (proprietaire) {
                item { SousTitre("Supprimer") }
                item {
                    TextButton(onClick = { supprimer = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Supprimer ce domaine", color = MaterialTheme.colorScheme.error)
                    }
                }
                item { Aide("Ses machines en sortent, son alias et ses groupes partent. Votre dernier domaine ne se supprime pas.") }
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
            title = { Text("Hébergement") },
            text = {
                Column {
                    options.forEach { option ->
                        ListItem(
                            leadingContent = { RadioButton(selected = choisi == option, onClick = { choisi = option }) },
                            headlineContent = { Text(option?.let { "Annuaire local ${it.abrege}" } ?: "Les racines") },
                            modifier = Modifier.clickable { choisi = option },
                        )
                    }
                    if (options.size == 1) Aide("Aucun annuaire local inscrit : déclarez-en un depuis Compte › Mon annuaire local.")
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
            title = { Text("Supprimer « ${domaine.affiche} » ?") },
            text = { Text("Ses machines en sortent, son alias, ses groupes et les droits qui le visent partent. Rien ne revient.") },
            confirmButton = {
                TextButton(onClick = {
                    supprimer = false
                    agir({ session.annuaire.supprimerDomaine(id) }, apres = { nav.popBackStack() })
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
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
                    texte, { texte = it }, label = { Text("Alias") }, singleLine = true,
                    isError = texte.isNotEmpty() && range == null, modifier = Modifier.fillMaxWidth(),
                )
                Aide(TEXTE_ALIAS_DE_DOMAINE)
            }
        },
        confirmButton = {
            TextButton(
                enabled = (facultatif && texte.isEmpty()) || (range != null && range != initial),
                onClick = { onValider(if (texte.isEmpty()) null else range) },
            ) { Text(if (facultatif) "Créer" else "Enregistrer") }
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

    Scaffold(
        topBar = { Barre("Mon annuaire local", nav) { TextButton(onClick = { declarer = true to null }) { Text("Déclarer") } } },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            code?.let { c ->
                item { SousTitre("Code d'inscription") }
                item { Aide(TEXTE_CODE_D_INSCRIPTION) }
                item {
                    ListItem(
                        headlineContent = { Text("asl-server --register ${c.code}", fontFamily = FontFamily.Monospace) },
                        supportingContent = { Text("Valable jusqu'au ${format.format(c.expireA)}") },
                    )
                }
            }
            if (inscriptions.isEmpty() && chargement.valeur != null) item { Aide("Aucun annuaire local déclaré.") }
            // Un annuaire par titulaire ; les déclarations qui attendent encore leur machine, à part.
            val parAnnuaire = inscriptions.groupBy { it.annuaire }
            parAnnuaire.forEach { (annuaire, membres) ->
                item { SousTitre(annuaire?.let { "Annuaire ${it.abrege}" } ?: "Déclaré, pas encore présenté") }
                items(membres) { inscription ->
                    ListItem(
                        headlineContent = { Text(inscription.adresse, fontFamily = FontFamily.Monospace) },
                        supportingContent = {
                            Column {
                                Text(libelleDInscription(inscription))
                                inscription.membre?.let { Text(it.texte, fontFamily = FontFamily.Monospace) }
                            }
                        },
                        trailingContent = if (inscription.membre != null && inscription.etat in setOf(EtatDInscription.EnAttente, EtatDInscription.Acceptee)) ({
                            TextButton(onClick = { retirer = inscription }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
                        }) else null,
                    )
                }
                val titulaireAccepte = membres.any { it.estTitulaire && it.etat == EtatDInscription.Acceptee }
                val vivants = membres.count { it.membre != null && it.etat in setOf(EtatDInscription.EnAttente, EtatDInscription.Acceptee) }
                if (annuaire != null && titulaireAccepte && vivants < 2) item {
                    TextButton(onClick = { declarer = true to annuaire }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Déclarer le second membre")
                    }
                }
            }
            item { Aide(TEXTE_ANNUAIRE_LOCAL) }
        }
    }

    if (declarer.first) {
        val second = declarer.second
        var adresse by remember { mutableStateOf("") }
        val forme = Alias.adresseDAnnuaire(adresse)
        AlertDialog(
            onDismissRequest = { declarer = false to null },
            title = { Text(if (second == null) "Déclarer mon annuaire local" else "Déclarer le second membre") },
            text = {
                Column {
                    OutlinedTextField(
                        adresse, { adresse = it }, label = { Text("hôte:port") }, singleLine = true,
                        isError = adresse.isNotEmpty() && forme == null, modifier = Modifier.fillMaxWidth(),
                    )
                    Aide("L'adresse où les racines et vos appareils le joignent, par exemple speedy.exemple.org:6630.")
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
                }) { Text("Déclarer") }
            },
            dismissButton = { TextButton(onClick = { declarer = false to null }) { Text("Annuler") } },
        )
    }
    retirer?.let { inscription ->
        val titulaire = inscription.estTitulaire
        AlertDialog(
            onDismissRequest = { retirer = null },
            title = { Text(if (titulaire) "Retirer l'annuaire ?" else "Retirer ce membre ?") },
            text = {
                Text(
                    if (titulaire) "L'annuaire entier est retiré, son second avec lui ; ses domaines reviennent aux racines."
                    else "Ce membre ne porte plus l'annuaire ; l'autre continue.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    retirer = null
                    portee.launch {
                        runCatching {
                            val annuaire = inscription.annuaire ?: return@runCatching
                            if (titulaire) session.annuaire.retirerAnnuaire(annuaire) else session.annuaire.retirerMembre(annuaire, inscription.membre!!)
                        }.onSuccess { erreur = null; chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
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

    Scaffold(topBar = { Barre("Inscriptions", nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            val liste = chargement.valeur
            if (chargement.valeur == null && chargement.erreur == null) return@LazyColumn
            if (liste.isNullOrEmpty()) item { Aide("Aucune inscription n'attend.") }
            items(liste.orEmpty()) { inscription ->
                ListItem(
                    headlineContent = { Text(inscription.adresse, fontFamily = FontFamily.Monospace) },
                    supportingContent = {
                        Column {
                            inscription.membre?.let { Text("Membre ${it.texte}", fontFamily = FontFamily.Monospace) }
                            inscription.proprietaire?.let { Text("Compte ${it.texte}", fontFamily = FontFamily.Monospace) }
                            inscription.annuaire?.takeIf { it != inscription.membre }?.let { Text("Second membre de ${it.abrege}") }
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { decision = inscription to true }) { Text("Accepter") }
                            TextButton(onClick = { decision = inscription to false }) { Text("Refuser", color = MaterialTheme.colorScheme.error) }
                        }
                    },
                )
            }
            item { Aide(TEXTE_ADMINISTRATION) }
        }
    }

    decision?.let { (inscription, accepte) ->
        AlertDialog(
            onDismissRequest = { decision = null },
            title = { Text(if (accepte) "Accepter cet annuaire ?" else "Refuser cet annuaire ?") },
            text = {
                Text(
                    if (accepte) "${inscription.adresse} pourra héberger les domaines de son propriétaire, et les racines serviront ses services."
                    else "Le refus l'emporte, y compris sur une acceptation d'un autre administrateur. Pour revenir dessus, il faudra une inscription neuve.",
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
                }) { Text(if (accepte) "Accepter" else "Refuser", color = if (accepte) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { decision = null }) { Text("Annuler") } },
        )
    }
}
