package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.Pastille
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.couleur
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.composants.resumeListe
import org.airdesktop.servicelocator.modele.Machine
import java.time.Instant

/** Les machines du compte : celles qui attendent leur enrôlement, puis les autres. */
@Composable
fun MachinesEcran(nav: NavController) {
    val session = LocalSession.current
    val chargement = rememberChargement { session.annuaire.machines() }
    val machines = chargement.valeur ?: emptyList()
    val enAttente = machines.filter { it.cle !is Machine.Cle.Enrolee }
    val enrolees = machines.filter { it.cle is Machine.Cle.Enrolee }

    Scaffold(
        topBar = { Barre("Machines") },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.DECLARER) }) { Icon(Icones.plus, "Déclarer une machine") }
        },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur) }
            if (enAttente.isNotEmpty()) {
                item { SousTitre("En attente d'enrôlement") }
                items(enAttente, key = { it.id.texte }) { machine -> LigneEnAttente(machine) { nav.navigate(Routes.machine(machine.id)) } }
                item { Aide("Ouvrez la machine pour lire le code à saisir sur place.") }
            }
            item { SousTitre("Mes machines") }
            if (machines.isEmpty() && chargement.erreur == null) {
                item { Aide("Aucune machine. Déclarez-en une pour obtenir son code d'enrôlement.") }
            }
            items(enrolees, key = { it.id.texte }) { machine ->
                ListItem(
                    headlineContent = { Text(machine.nom) },
                    supportingContent = { Text(machine.resumeListe) },
                    leadingContent = { Pastille(machine.couleur) },
                    trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { nav.navigate(Routes.machine(machine.id)) },
                )
            }
            if (enrolees.isNotEmpty()) {
                item { Aide("Le point dit si la machine tient une connexion à l'annuaire. Il ne dit pas qu'un service est joignable.") }
            }
            item { Box(Modifier.padding(bottom = 88.dp)) }
        }
    }
}

@Composable
private fun LigneEnAttente(machine: Machine, ouvrir: () -> Unit) {
    var maintenant by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); maintenant = Instant.now() } }
    val sous = when (val cle = machine.cle) {
        // Déclarée d'un autre appareil : le code n'est connu que de lui.
        is Machine.Cle.Attendue -> cle.code?.let { sousTitreCode(it, maintenant) } ?: "Pas de clé — émettez un code d'enrôlement"
        is Machine.Cle.Revoquee -> cle.code?.let { sousTitreCode(it, maintenant) } ?: "Clé révoquée — émettez un code pour ré-enrôler"
        is Machine.Cle.Enrolee -> ""
    }
    ListItem(
        headlineContent = { Text(machine.nom) },
        supportingContent = { Text(sous) },
        leadingContent = { Icon(Icones.horloge, null, tint = Couleurs.attention) },
        trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = ouvrir),
    )
}

private fun sousTitreCode(code: org.airdesktop.servicelocator.modele.CodeEnrolement, maintenant: Instant) =
    if (code.estValide(maintenant)) "Code d'enrôlement valable encore ${Formats.minutesSecondes(code.reste(maintenant))}"
    else "Code d'enrôlement expiré — émettez-en un nouveau"
