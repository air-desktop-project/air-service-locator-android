package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.Routes
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.ChoixCapacites
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.Machine

/** Déclarer une machine : un nom, des capacités, et le code en retour. */
@Composable
fun DeclarerMachineEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    var nom by remember { mutableStateOf("") }
    var capacites by remember { mutableStateOf(emptySet<Capacite>()) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    Scaffold(topBar = { Barre("Nouvelle machine", nav) }) { marges ->
        Column(Modifier.fillMaxSize().padding(marges)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    nom, { nom = it }, label = { Text("Nom d'hôte") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    isError = nom.isNotEmpty() && !Machine.nomValide(nom),
                    supportingText = { NomDHoteSousLeChamp(nom) },
                )
                Aide(TEXTE_NOM_D_HOTE + " Pour un nom libre — accents, espaces, émoji —, posez un alias sur la fiche de la machine une fois déclarée.")
                SousTitre("Capacités")
                ChoixCapacites(capacites) { capacites = it }
                Aide("Rien n'est coché d'avance. Une machine qui porte les deux a un rayon de dégât plus large : un daemon compromis pourrait aussi énumérer tout ce que vous avez le droit de voir.")
                Erreur(erreur)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = { nav.popBackStack() }) { Text("Annuler") }
                Button(
                    enabled = !enCours && Machine.nomValide(nom),
                    onClick = {
                        portee.launch {
                            enCours = true
                            runCatching { session.annuaire.declarerMachine(nom, capacites) }
                                .onSuccess { machine ->
                                    // Le code remplace la déclaration dans la pile : le retour ramène aux machines.
                                    nav.navigate(Routes.code(machine.id)) { popUpTo(Routes.DECLARER) { inclusive = true } }
                                }
                                .onFailure { erreur = it.messageAnnuaire }
                            enCours = false
                        }
                    },
                ) { Text("Déclarer") }
            }
        }
    }
}

/** Ce que dit l'aide du nom d'une machine, à la déclaration comme au renommage. */
internal const val TEXTE_NOM_D_HOTE = "Lettres, chiffres et tirets, sans espace ni accent — c'est le nom d'hôte de la machine, rangé en minuscules."

/**
 * Sous le champ du nom : la forme que l'annuaire rangera quand elle diffère de la saisie (« Grenier » → « grenier »),
 * ou pourquoi il la refuserait. Rien tant que le champ est vide.
 */
@Composable
internal fun NomDHoteSousLeChamp(nom: String) {
    if (nom.isEmpty()) return
    val range = Machine.nomDHote(nom)
    when {
        range == null -> Text("Pas un nom d'hôte : lettres, chiffres et tirets, 63 au plus, sans tiret au bord.")
        range != nom -> Text("Sera rangé « $range ».")
    }
}
