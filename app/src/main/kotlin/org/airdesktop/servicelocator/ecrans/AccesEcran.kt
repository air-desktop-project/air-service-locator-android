package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Machine

/** Les autorisations, dans les deux sens. Les révoquées restent, barrées. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccesEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.autorisations() to session.annuaire.machines() }
    val (autorisations, machines) = chargement.valeur ?: (emptyList<Autorisation>() to emptyList<Machine>())
    val moi = session.compte?.identifiant
    val accordees = autorisations.filter { it.accordeePar == moi }
    val recues = autorisations.filter { it.accordeeA == moi }
    var aRevoquer by remember { mutableStateOf<Autorisation?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { Barre("Accès") },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.ACCORDER) }) { Icon(Icones.plus, "Accorder un accès") }
        },
    ) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur ?: erreur) }
            item { SousTitre("Accordés par moi") }
            if (accordees.isEmpty()) item { Aide("Vous n'avez accordé aucun accès.") }
            items(accordees, key = { it.id.texte }) { autorisation ->
                LigneAutorisation(
                    autorisation, machines, Sens.ACCORDEE,
                    Modifier.combinedClickable(onClick = {}, onLongClick = { if (!autorisation.estRevoquee) aRevoquer = autorisation }),
                )
            }
            item { Aide("Retirer suffit : il n'y a aucun jeton à récupérer. Appui long pour révoquer ; les accès révoqués restent visibles, barrés.") }
            item { SousTitre("Accordés à moi") }
            if (recues.isEmpty()) item { Aide("Personne ne vous a encore accordé d'accès.") }
            items(recues, key = { it.id.texte }) { LigneAutorisation(it, machines, Sens.RECUE, Modifier) }
            item { Aide("Vos machines portant la capacité « lecture » peuvent résoudre ces services.") }
            item { Box(Modifier.padding(bottom = 88.dp)) }
        }
    }

    aRevoquer?.let { autorisation ->
        AlertDialog(
            onDismissRequest = { aRevoquer = null },
            title = { Text("Révoquer cet accès ?") },
            text = { Text("Effet immédiat. Les machines de ce compte ne pourront plus résoudre ce que cet accès ouvrait.") },
            confirmButton = {
                TextButton(onClick = {
                    aRevoquer = null
                    portee.launch {
                        runCatching { session.annuaire.revoquerAutorisation(autorisation.id) }
                            .onSuccess { chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
                    }
                }) { Text("Révoquer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { aRevoquer = null }) { Text("Annuler") } },
        )
    }
}

enum class Sens { ACCORDEE, RECUE }

/**
 * L'annuaire ne connaît ni nom ni courriel : ce que l'on peut montrer de
 * l'autre compte est son identifiant, et — pour ce que l'on a accordé —
 * l'étiquette que l'on a posée soi-même.
 */
@Composable
fun LigneAutorisation(autorisation: Autorisation, machines: List<Machine>, sens: Sens, modifier: Modifier) {
    val titre = when (sens) {
        Sens.ACCORDEE -> autorisation.etiquette.ifEmpty { autorisation.accordeeA.abrege }
        Sens.RECUE -> autorisation.accordeePar.abrege
    }
    val portee = when (val p = autorisation.portee) {
        Autorisation.Portee.Tout -> if (sens == Sens.ACCORDEE) "Tout mon compte" else "Tout son compte"
        is Autorisation.Portee.Machine -> "Machine ${machines.firstOrNull { it.id == p.id }?.nom ?: p.id.abrege}"
        is Autorisation.Portee.Service -> "Service ${machines.flatMap { it.services }.firstOrNull { it.id == p.id }?.nom ?: p.id.abrege}"
    }
    val sous = buildList {
        if (sens == Sens.ACCORDEE && autorisation.etiquette.isNotEmpty()) add(autorisation.accordeeA.abrege)
        add(portee)
        autorisation.revoqueeLe?.let { add("révoquée ${Formats.relatif(it)}") }
    }.joinToString(" · ")
    val barre = autorisation.estRevoquee
    ListItem(
        headlineContent = {
            Text(
                titre,
                fontFamily = if (titre.startsWith("u-")) FontFamily.Monospace else null,
                textDecoration = if (barre) TextDecoration.LineThrough else null,
                color = if (barre) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { Text(sous) },
        modifier = modifier,
    )
}
