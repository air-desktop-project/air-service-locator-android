package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.ChoixCapacites
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneIdentifiant
import org.airdesktop.servicelocator.composants.Pastille
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.couleur
import org.airdesktop.servicelocator.composants.detailEtat
import org.airdesktop.servicelocator.composants.libelleEtat
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire

/** Une machine : ses services et leur joignabilité, son identité, sa clé. */
@Composable
fun MachineEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.machines().firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable }
    val machine = chargement.valeur
    var erreur by remember { mutableStateOf<String?>(null) }
    var renommer by remember { mutableStateOf(false) }
    var confirmerRevocation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Barre(machine?.nom ?: "", nav) {
                TextButton(onClick = { renommer = true }, enabled = machine != null) { Text("Renommer") }
            }
        },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            if (machine == null) return@LazyColumn
            if (Capacite.ANNONCE in machine.capacites) {
                item { SousTitre("Services") }
                if (machine.services.isEmpty()) item { Aide("Aucun service annoncé pour l'instant.") }
                items(machine.services, key = { it.id.texte }) { LigneService(it, Modifier.clickable { nav.navigate(Routes.service(machine.id, it.id)) }) }
                item { Aide("« Joignable » veut dire : l'annuaire a lui-même ouvert une connexion vers ce port, à cette date. Un point d'écoute UDP ne se sonde pas.") }
            }
            item { SousTitre("Machine") }
            item { LigneIdentifiant("Identifiant public", machine.id) }
            item {
                ListItem(
                    headlineContent = { Text("Capacités") },
                    supportingContent = { Text(machine.capacitesTexte.ifEmpty { "aucune" }) },
                    trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.navigate(Routes.capacites(machine.id)) },
                )
            }
            item { LigneCle(machine) }
            item {
                Column(Modifier.padding(16.dp)) {
                    when (machine.cle) {
                        is Machine.Cle.Enrolee -> {
                            FilledTonalButton(
                                onClick = { confirmerRevocation = true },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icones.cle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Révoquer la clé")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Effet immédiat : connexions fermées, baux tombés. La machine reste, il faudra la ré-enrôler sur place.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is Machine.Cle.Attendue, is Machine.Cle.Revoquee -> {
                            Button(onClick = { nav.navigate(Routes.code(machine.id)) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icones.terminal, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Code d'enrôlement")
                            }
                        }
                    }
                }
            }
        }
    }

    if (renommer && machine != null) {
        var nom by remember { mutableStateOf(machine.nom) }
        AlertDialog(
            onDismissRequest = { renommer = false },
            title = { Text("Renommer la machine") },
            text = {
                Column {
                    Text("Pour vous, jamais pour la machine. Un nom ne retire aucun droit.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(nom, { nom = it }, label = { Text("Nom") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = Machine.nomValide(nom),
                    onClick = {
                        renommer = false
                        portee.launch {
                            runCatching { session.annuaire.modifierMachine(id, nom = nom) }
                                .onSuccess { chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                        }
                    },
                ) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renommer = false }) { Text("Annuler") } },
        )
    }

    if (confirmerRevocation && machine != null) {
        AlertDialog(
            onDismissRequest = { confirmerRevocation = false },
            title = { Text("Révoquer la clé de ${machine.nom} ?") },
            text = { Text("Les connexions de la machine sont fermées à la seconde et ses annonces tombent. Elle garde son nom, ses capacités et ses services ; il faudra saisir un nouveau code sur place.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmerRevocation = false
                    portee.launch {
                        runCatching { session.annuaire.revoquerCle(id) }
                            .onSuccess { chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text("Révoquer la clé", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmerRevocation = false }) { Text("Annuler") } },
        )
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
                Text(service.pointsTexte)
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

@Composable
private fun LigneCle(machine: Machine) {
    val (icone, teinte, texte) = when (val cle = machine.cle) {
        is Machine.Cle.Enrolee -> Triple(Icones.coche, Couleurs.joignable, cle.le?.let { "enrôlée ${Formats.relatif(it)}" } ?: "enrôlée")
        is Machine.Cle.Attendue -> Triple(Icones.horloge, Couleurs.attention, "pas encore enrôlée")
        is Machine.Cle.Revoquee -> Triple(Icones.croix, Couleurs.erreur, "révoquée ${Formats.relatif(cle.le)}")
    }
    ListItem(
        leadingContent = { Icon(icone, null, tint = teinte) },
        headlineContent = { Text("Clé") },
        supportingContent = { Text(texte) },
    )
}

/** Les capacités d'une machine, modifiables — `PATCH /v1/machines/{m}`. */
@Composable
fun CapacitesEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.machines().firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable }
    var erreur by remember { mutableStateOf<String?>(null) }
    val machine = chargement.valeur

    Scaffold(topBar = { Barre("Capacités", nav) }) { marges ->
        Column(Modifier.padding(marges)) {
            Erreur(chargement.erreur ?: erreur)
            if (machine != null) {
                ChoixCapacites(machine.capacites) { nouvelles ->
                    portee.launch {
                        runCatching { session.annuaire.modifierMachine(id, capacites = nouvelles) }
                            .onSuccess { chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }
                Aide("Retirer l'annonce ferme les connexions de la machine et fait tomber ses baux. Retirer la lecture ne ferme rien : sa prochaine demande sera refusée.")
            }
        }
    }
}

