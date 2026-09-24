package org.airdesktop.servicelocator.ecrans

import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.BuildConfig
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneIdentifiant
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.reseau.AnnuaireSimule
import java.util.Optional

/** Le compte : son identifiant public, son alias, ses appareils, son annuaire. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompteEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.rafraichirCompte(); session.annuaire.appareils() }
    val appareils = chargement.valeur ?: emptyList()
    // Les révoqués ne s'affichent pas par défaut : ils restent dans l'annuaire, marqués, mais une liste qui les mêle
    // aux vivants dit mal combien d'appareils tiennent le compte. Le réglage est retenu d'une fois sur l'autre.
    val contexte = LocalContext.current
    val reglages = remember { contexte.getSharedPreferences("affichage", Context.MODE_PRIVATE) }
    var revoquesVisibles by remember { mutableStateOf(reglages.getBoolean("appareils.revoques.visibles", false)) }
    val nombreDeRevoques = appareils.count { it.estRevoque }
    val appareilsMontres = if (revoquesVisibles) appareils else appareils.filter { !it.estRevoque }
    // La version de l'annuaire ne conditionne rien : si elle manque, l'écran le dit, sans en faire une erreur de la page.
    // `null` tant qu'on n'a pas demandé, `Optional.empty()` si l'annuaire ne sait pas la dire.
    var versionAnnuaire by remember { mutableStateOf<Optional<String>?>(null) }
    var aRevoquer by remember { mutableStateOf<Appareil?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    // Effacer le compte : la demande (le dialogue), puis l'attente de l'annuaire, puis son refus s'il refuse — dit
    // à côté du bouton, en bas, là où l'on regarde à ce moment-là.
    var effacementDemande by remember { mutableStateOf(false) }
    var effacementEnCours by remember { mutableStateOf(false) }
    var erreurEffacement by remember { mutableStateOf<String?>(null) }
    val compte = session.compte
    LaunchedEffect(compte) { if (compte != null) versionAnnuaire = Optional.ofNullable(runCatching { session.annuaire.annonce()?.version }.getOrNull()) }

    Scaffold(topBar = { Barre("Compte") }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            if (compte != null) {
                item { SousTitre("Identité") }
                item { LigneIdentifiant("Identifiant public", compte.identifiant, partageable = true) }
                item {
                    ListItem(
                        headlineContent = { Text("Alias public") },
                        supportingContent = { Text(compte.alias ?: "aucun") },
                        trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { nav.navigate(Routes.ALIAS) },
                    )
                }
                item { Aide("À donner à qui doit vous accorder un accès. Un alias est public et devinable ; sans alias, seul l'identifiant vous rend trouvable.") }
            }
            item { SousTitre("Appareils") }
            items(appareilsMontres, key = { it.id.texte }) { appareil ->
                // La biométrie quand on la connaît (cet appareil), sinon la plate-forme déclarée, sinon un téléphone.
                val icone = when (appareil.biometrie) {
                    Appareil.Biometrie.VISAGE -> Icones.visage
                    Appareil.Biometrie.EMPREINTE -> Icones.empreinte
                    null -> if (appareil.description?.plateforme == Appareil.Plateforme.MACOS) Icones.machine else Icones.telephone
                }
                val teinte = if (appareil.estRevoque) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant
                // Cet appareil se nomme par ce que le système en dit ; les autres portent le modèle qu'ils ont
                // déclaré à l'annuaire, ou le repli s'ils ne l'ont pas encore fait.
                val nom = if (appareil.estCeluiCi) "${Build.MANUFACTURER} ${Build.MODEL}" else appareil.titre
                // Ce que l'on sait, et rien de plus : une date quand cet appareil l'a vue, l'attestation quand l'annuaire l'a rendue.
                val sous = if (appareil.estRevoque) {
                    appareil.revoqueLe?.let { "Révoqué le ${Formats.jour(it)}" } ?: "Révoqué"
                } else {
                    buildList {
                        if (appareil.estCeluiCi) add("Cet appareil")
                        add(appareil.enroleLe?.let { "enrôlé le ${Formats.jour(it)}" } ?: "enrôlé")
                        when (appareil.biometrie) {
                            Appareil.Biometrie.VISAGE -> add("visage")
                            Appareil.Biometrie.EMPREINTE -> add("empreinte")
                            null -> Unit
                        }
                        when (appareil.attestation) {
                            Appareil.Attestation.APPLE -> add("attesté par Apple")
                            Appareil.Attestation.ANDROID -> add("clé attestée (Android)")
                            Appareil.Attestation.INVITATION -> add("sur invitation")
                            Appareil.Attestation.AUCUNE -> add("sans attestation")
                            Appareil.Attestation.ATTENDUE -> add("en attente d'attestation")
                            null -> Unit
                        }
                        when (appareil.description?.plateforme) {
                            Appareil.Plateforme.IOS -> add("iOS")
                            Appareil.Plateforme.ANDROID -> add("Android")
                            Appareil.Plateforme.MACOS -> add("macOS")
                            null -> Unit
                        }
                    }.joinToString(" · ")
                }
                ListItem(
                    leadingContent = { Icon(icone, null, tint = teinte) },
                    headlineContent = { Text(nom, color = if (appareil.estRevoque) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                    supportingContent = {
                        Column {
                            Text(sous)
                            // L'identifiant est la seule chose que l'annuaire sait d'un appareil, et la seule qui permette
                            // de le reconnaître d'un écran à l'autre : « Autre appareil » ne dit rien, `a-…` dit lequel.
                            Text(appareil.id.texte, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    // Le geste est VISIBLE : un bouton, comme sur le Mac — l'appui long reste, pour qui l'a pris.
                    trailingContent = {
                        if (!appareil.estRevoque && !appareil.estCeluiCi) {
                            TextButton(onClick = { aRevoquer = appareil }) { Text("Révoquer", color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {
                        if (!appareil.estRevoque && !appareil.estCeluiCi) aRevoquer = appareil
                    }),
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icones.plus, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Enrôler un autre appareil", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.navigate(Routes.ENROLER_APPAREIL) },
                )
            }
            if (nombreDeRevoques > 0) {
                item {
                    ListItem(
                        headlineContent = { Text("Voir les appareils révoqués ($nombreDeRevoques)") },
                        trailingContent = {
                            Switch(checked = revoquesVisibles, onCheckedChange = {
                                revoquesVisibles = it
                                reglages.edit().putBoolean("appareils.revoques.visibles", it).apply()
                            })
                        },
                    )
                }
            }
            item { Aide("L'annuaire ne connaît de chaque appareil que son identifiant : c'est lui qui dit si un appareil est bien l'un des vôtres — comparez-le à celui que l'autre appareil affiche pour lui-même. Un appareil que vous ne reconnaissez pas se révoque. Un appareil ne peut pas se révoquer lui-même ; « Révoquer » (ou un appui long) en révoque un autre ; révoqué, il reste dans l'annuaire, marqué, et « Voir les appareils révoqués » le montre. Un compte sur un seul appareil est un compte qu'un téléphone perdu ferme — et efface, à trente jours : avec un seul appareil, perdre ce téléphone efface ce compte.") }
            item { SousTitre("Annuaire") }
            item { ListItem(headlineContent = { Text("Annuaire") }, supportingContent = { Text("racines air-desktop-project") }) }
            // Les deux versions, l'application et l'annuaire, lisibles ici parce que c'est l'écran où l'on va quand
            // quelque chose ne va pas — et qu'un écart entre les deux est souvent la réponse.
            item {
                ListItem(
                    headlineContent = { Text("Version de l'application") },
                    supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontFamily = FontFamily.Monospace) },
                )
            }
            item {
                val texte = when (val v = versionAnnuaire) {
                    null -> "…"
                    else -> v.orElse("ne la dit pas")
                }
                ListItem(
                    headlineContent = { Text("Version de l'annuaire") },
                    supportingContent = { Text(texte, fontFamily = FontFamily.Monospace) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Ce qui est exposé de moi") },
                    supportingContent = { Text("Par relation entre annuaires, et ce que vous en retirez.") },
                    trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.navigate(Routes.EXPOSITIONS) },
                )
            }
            // Tout en bas, et visible : le geste qui ferme le compte depuis cet appareil — le dernier acte de sa clé
            // (`docs/modele.md` §2.1). Le dialogue dit ce qui part ; ici, seulement que ça ne revient pas.
            if (compte != null) {
                item { SousTitre("Effacer") }
                item {
                    TextButton(
                        onClick = { effacementDemande = true },
                        enabled = !effacementEnCours,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            if (effacementEnCours) "Effacement en cours…" else "Effacer mon compte",
                            color = if (effacementEnCours) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item { Aide("Le compte, ses appareils, ses machines, ses accès et son alias quittent l'annuaire, et cet appareil revient à l'écran d'accueil. Rien ne revient.") }
                item { Erreur(erreurEffacement) }
            }
        }
    }

    if (effacementDemande) {
        AlertDialog(
            onDismissRequest = { effacementDemande = false },
            title = { Text("Effacer mon compte ?") },
            text = { Text("Tous vos appareils, vos machines et leurs services, vos accès donnés et reçus, votre alias. Rien ne revient.") },
            confirmButton = {
                TextButton(onClick = {
                    effacementDemande = false
                    effacementEnCours = true
                    erreurEffacement = null
                    portee.launch {
                        // Réussi, le compte est `null` et l'accueil a déjà remplacé cet écran ; refusé, on le dit ici.
                        runCatching { session.effacerCompte() }.onFailure { erreurEffacement = it.messageAnnuaire }
                        effacementEnCours = false
                    }
                }) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { effacementDemande = false }) { Text("Annuler") } },
        )
    }

    aRevoquer?.let { appareil ->
        AlertDialog(
            onDismissRequest = { aRevoquer = null },
            title = { Text("Révoquer ${appareil.nom} ?") },
            text = { Text("Cet appareil ne pourra plus administrer le compte. Il reste dans la liste, marqué.") },
            confirmButton = {
                TextButton(onClick = {
                    aRevoquer = null
                    portee.launch {
                        runCatching { session.annuaire.revoquerAppareil(appareil.id) }
                            .onSuccess { chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text("Révoquer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { aRevoquer = null }) { Text("Annuler") } },
        )
    }
}

/** L'alias public — la seule donnée que l'utilisateur nous confie. */
@Composable
fun AliasEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    var alias by remember { mutableStateOf(session.compte?.alias ?: "") }
    var erreur by remember { mutableStateOf<String?>(null) }

    fun definir(valeur: String?) = portee.launch {
        runCatching { session.definirAlias(valeur) }.onSuccess { nav.popBackStack() }.onFailure { erreur = it.messageAnnuaire }
    }

    Scaffold(
        topBar = {
            Barre("Alias public", nav) {
                TextButton(
                    enabled = AnnuaireSimule.aliasValide(alias) && alias != session.compte?.alias,
                    onClick = { definir(alias) },
                ) { Text("Enregistrer") }
            }
        },
    ) { marges ->
        Column(Modifier.padding(marges)) {
            OutlinedTextField(
                alias, { alias = it }, label = { Text("alias") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Aide("Lettres, chiffres et tirets, de 3 à 32. Il est public par construction : quiconque peut essayer un alias et découvrir qu'il existe. Il ne rend rien d'autre que votre identifiant.")
            Erreur(erreur)
            if (session.compte?.alias != null) {
                TextButton(onClick = { definir(null) }, modifier = Modifier.padding(16.dp)) {
                    Text("Retirer l'alias", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * `GET /v1/expositions` rend `501` aujourd'hui, et c'est exact : la table des
 * relations entre annuaires n'est pas écrite. Dire « rien n'est exposé » serait
 * pire.
 */
@Composable
fun ExpositionsEcran(nav: NavController) {
    Scaffold(topBar = { Barre("Expositions", nav) }) { marges ->
        Indisponible(
            Modifier.padding(marges), Icones.branche, "L'annuaire ne sait pas encore le dire",
            "Ce qui est exposé de vous, relation par relation, apparaîtra ici quand la fédération entre annuaires sera écrite. Vous pourrez alors en retirer votre compte, ou telle de vos machines.",
        )
    }
}

@Composable
private fun Indisponible(modifier: Modifier, icone: ImageVector, titre: String, texte: String) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(icone, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(titre, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
