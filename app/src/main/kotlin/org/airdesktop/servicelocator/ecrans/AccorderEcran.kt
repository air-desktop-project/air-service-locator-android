package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant

private enum class Verdict { VIDE, RECHERCHE, EXISTE, INCONNU, MAL_FORME }
private enum class ChoixPortee { TOUT, MACHINE, SERVICE }

/**
 * Accorder un accès à un autre compte : son identifiant ou son alias, une
 * portée, une étiquette — et ce que cela révèle, dit avant, pas après.
 */
@Composable
fun AccorderEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val machines = rememberChargement { session.annuaire.machines() }.valeur ?: emptyList()
    var saisie by remember { mutableStateOf("") }
    var beneficiaire by remember { mutableStateOf<Identifiant?>(null) }
    var verdict by remember { mutableStateOf(Verdict.VIDE) }
    var choix by remember { mutableStateOf(ChoixPortee.TOUT) }
    var machine by remember { mutableStateOf<Identifiant?>(null) }
    var service by remember { mutableStateOf<Identifiant?>(null) }
    var etiquette by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }

    // La saisie confirme que le destinataire existe : sans quoi une faute de
    // frappe produit une autorisation muette accordée à personne.
    LaunchedEffect(saisie) {
        val texte = saisie.trim()
        if (texte.isEmpty()) { verdict = Verdict.VIDE; beneficiaire = null; return@LaunchedEffect }
        verdict = Verdict.RECHERCHE
        delay(300)
        try {
            if (texte.length == Identifiant.LONGUEUR_TEXTE || (texte.contains('-') && texte.length > 20)) {
                val id = Identifiant.analyser(texte, Genre.UTILISATEUR)
                val existe = session.annuaire.utilisateurExiste(id)
                beneficiaire = if (existe) id else null
                verdict = if (existe) Verdict.EXISTE else Verdict.INCONNU
            } else {
                val id = session.annuaire.identifiantPourAlias(texte)
                beneficiaire = id
                verdict = if (id != null) Verdict.EXISTE else Verdict.INCONNU
            }
        } catch (e: Identifiant.Erreur) {
            beneficiaire = null
            verdict = Verdict.MAL_FORME
        } catch (e: Exception) {
            erreur = e.messageAnnuaire
        }
    }

    val porteeChoisie: Autorisation.Portee? = when (choix) {
        ChoixPortee.TOUT -> Autorisation.Portee.Tout
        ChoixPortee.MACHINE -> machine?.let { Autorisation.Portee.Machine(it) }
        ChoixPortee.SERVICE -> service?.let { Autorisation.Portee.Service(it) }
    }
    val peutAccorder = verdict == Verdict.EXISTE && beneficiaire != null && porteeChoisie != null && !enCours

    Scaffold(topBar = { Barre("Accorder un accès", nav) }) { marges ->
        Column(Modifier.fillMaxSize().padding(marges)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    saisie, { saisie = it }, label = { Text("Identifiant ou alias") }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LigneVerdict(verdict)
                Aide("Un identifiant u-… que la personne vous a transmis, ou son alias public. L'annuaire ne connaît ni nom, ni courriel.")

                SousTitre("Portée")
                Choix("Tout mon compte", null, choix == ChoixPortee.TOUT) { choix = ChoixPortee.TOUT }
                Choix("Une machine", machines.firstOrNull { it.id == machine }?.nom, choix == ChoixPortee.MACHINE) { choix = ChoixPortee.MACHINE }
                if (choix == ChoixPortee.MACHINE) {
                    Menu(machines.map { it.id to it.nom }, machine) { machine = it }
                }
                val services = machines.flatMap { m -> m.services.map { s -> s.id to "${m.nom} · ${s.nom}" } }
                Choix("Un service", services.firstOrNull { it.first == service }?.second, choix == ChoixPortee.SERVICE) { choix = ChoixPortee.SERVICE }
                if (choix == ChoixPortee.SERVICE) {
                    Menu(services, service) { service = it }
                }

                OutlinedTextField(
                    etiquette, { etiquette = it }, label = { Text("Étiquette") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Aide("Pour savoir ce que vous révoquez dans six mois.")

                Row(
                    Modifier.padding(16.dp).background(Couleurs.attentionFond, RoundedCornerShape(12.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icones.alerte, null, tint = Couleurs.attention)
                    Text(
                        buildAnnotatedString {
                            append("Ce compte verra les ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("noms") }
                            append(" de vos machines et services concernés, leurs ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("adresses IP réelles") }
                            append(" et ports, et leur état de joignabilité.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Erreur(erreur)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { nav.popBackStack() }) { Text("Annuler") }
                Button(
                    enabled = peutAccorder,
                    onClick = {
                        portee.launch {
                            enCours = true
                            runCatching { session.annuaire.accorder(beneficiaire!!, porteeChoisie!!, etiquette) }
                                .onSuccess { nav.popBackStack() }.onFailure { erreur = it.messageAnnuaire }
                            enCours = false
                        }
                    },
                ) { Text("Accorder") }
            }
        }
    }
}

@Composable
private fun LigneVerdict(verdict: Verdict) {
    val (texte, couleur, icone) = when (verdict) {
        Verdict.VIDE -> return
        Verdict.RECHERCHE -> Triple("Vérification…", MaterialTheme.colorScheme.onSurfaceVariant, null)
        Verdict.EXISTE -> Triple("Ce compte existe.", Couleurs.joignable, Icones.coche)
        Verdict.INCONNU -> Triple("Aucun compte sous cet identifiant ou cet alias.", Couleurs.attention, null)
        Verdict.MAL_FORME -> Triple("Ce n'est ni un identifiant u-… ni un alias.", Couleurs.erreur, Icones.croix)
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icone != null) Icon(icone, null, Modifier.width(16.dp), tint = couleur)
        Text(texte, style = MaterialTheme.typography.bodySmall, color = couleur, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Choix(titre: String, sous: String?, coche: Boolean, choisir: () -> Unit) {
    ListItem(
        headlineContent = { Text(titre) },
        supportingContent = sous?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = coche, onClick = choisir) },
        modifier = Modifier.clickable(onClick = choisir),
    )
}

@Composable
private fun Menu(options: List<Pair<Identifiant, String>>, choisi: Identifiant?, choisir: (Identifiant) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { ouvert = true }) { Text(options.firstOrNull { it.first == choisi }?.second ?: "Choisir…") }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            for ((id, nom) in options) DropdownMenuItem(text = { Text(nom) }, onClick = { choisir(id); ouvert = false })
        }
        Spacer(Modifier.height(4.dp))
    }
}
