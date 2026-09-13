package org.airdesktop.servicelocator.ecrans

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
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

/** Le compte : son identifiant public, son alias, ses appareils, son annuaire. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompteEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.rafraichirCompte(); session.annuaire.appareils() }
    val appareils = chargement.valeur ?: emptyList()
    var aRevoquer by remember { mutableStateOf<Appareil?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val compte = session.compte

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
            items(appareils, key = { it.id.texte }) { appareil ->
                val icone = when (appareil.biometrie) {
                    Appareil.Biometrie.VISAGE -> Icones.visage
                    Appareil.Biometrie.EMPREINTE -> Icones.empreinte
                    null -> Icones.telephone
                }
                val teinte = if (appareil.estRevoque) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant
                // L'annuaire ne connaît aucun nom : celui de cet appareil vient du téléphone lui-même.
                val nom = if (appareil.estCeluiCi) "${Build.MANUFACTURER} ${Build.MODEL}" else appareil.nom
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
                            Appareil.Attestation.GOOGLE -> add("attesté par Google")
                            Appareil.Attestation.AUCUNE -> add("sans attestation")
                            null -> Unit
                        }
                    }.joinToString(" · ")
                }
                ListItem(
                    leadingContent = { Icon(icone, null, tint = teinte) },
                    headlineContent = { Text(nom, color = if (appareil.estRevoque) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(sous) },
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
            item { Aide("Un appareil ne peut pas se révoquer lui-même ; appui long pour en révoquer un autre, qui reste alors dans la liste. Un compte sur un seul appareil est un compte qu'un téléphone perdu ferme.") }
            item { SousTitre("Annuaire") }
            item { ListItem(headlineContent = { Text("Annuaire") }, supportingContent = { Text("racines air-desktop-project") }) }
            item {
                ListItem(
                    headlineContent = { Text("Ce qui est exposé de moi") },
                    supportingContent = { Text("Par relation entre annuaires, et ce que vous en retirez.") },
                    trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.navigate(Routes.EXPOSITIONS) },
                )
            }
        }
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
