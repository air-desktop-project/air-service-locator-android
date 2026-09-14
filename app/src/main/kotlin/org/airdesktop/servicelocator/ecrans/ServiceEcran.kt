package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneIdentifiant
import org.airdesktop.servicelocator.composants.Pastille
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.couleur
import org.airdesktop.servicelocator.composants.detail
import org.airdesktop.servicelocator.composants.detailEtat
import org.airdesktop.servicelocator.composants.libelle
import org.airdesktop.servicelocator.composants.libelleEtat
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Candidat
import org.airdesktop.servicelocator.modele.Diagnostic
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire

/**
 * Un service : ce que l'annuaire en affirme, point d'écoute par point
 * d'écoute, et ce qu'il a répondu au daemon à son annonce.
 *
 * L'écran ne résume pas : la liste l'a fait. Ici, chaque point porte son
 * verdict et sa date, chaque candidat son origine — parce que « joignable »
 * se lit avec « depuis où » et « quand », ou ne se lit pas.
 */
@Composable
fun ServiceEcran(nav: NavController, machine: Identifiant, id: Identifiant) {
    val session = LocalSession.current
    val chargement = rememberChargement {
        val m = session.annuaire.machines().firstOrNull { it.id == machine } ?: throw ErreurAnnuaire.Introuvable
        m to (m.services.firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable)
    }
    val (fiche, service) = chargement.valeur ?: (null to null)

    Scaffold(topBar = { Barre(service?.nom ?: "", nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges)) {
            item { Erreur(chargement.erreur) }
            if (fiche == null || service == null) return@LazyColumn
            item {
                ListItem(
                    leadingContent = { Pastille(service.couleur, 10) },
                    headlineContent = { Text(service.libelleEtat, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = {
                        Column {
                            Text(service.detailEtat)
                            (service.etat as? Service.Etat.Annonce)?.let { Text("annoncé ${Formats.relatif(it.depuis)}") }
                            if (service.oscille) Text("Deux daemons de ce nom se chassent l'un l'autre : chaque annonce remplace la précédente.", color = Couleurs.attention)
                        }
                    },
                )
            }
            item { SousTitre("Points d'écoute") }
            items(service.points, key = { it.texte }) { point -> LignePoint(point.texte, service.joignabilite[point]) }
            item { Aide("Le verdict est celui de l'annuaire, qui a lui-même essayé d'ouvrir une connexion vers ce port. Un point UDP ne se sonde pas : aucune poignée de main, aucun écho générique.") }
            if (service.candidats.isNotEmpty()) {
                item { SousTitre("Candidats") }
                items(service.candidats) { candidat ->
                    ListItem(
                        headlineContent = { Text(adresse(candidat), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = {
                            Text(
                                if (candidat.origine == Candidat.Origine.REFLEXIF) "observé par l'annuaire sur la connexion d'annonce"
                                else "annoncé par le daemon — vrai sur son réseau, souvent faux ailleurs",
                            )
                        },
                    )
                }
                item { Aide("Où l'on peut essayer de joindre ce service, IPv6 d'abord. C'est ce qu'une machine autorisée reçoit quand elle demande où le joindre.") }
            }
            service.diagnostic?.let { diagnostic ->
                item { SousTitre("Ce que l'annuaire a répondu au daemon") }
                diagnostic.vuDepuis?.let { vu ->
                    item { ListItem(headlineContent = { Text("Vu depuis") }, supportingContent = { Text(vu, fontFamily = FontFamily.Monospace) }) }
                }
                diagnostic.derriereNat?.let { nat ->
                    item { ListItem(headlineContent = { Text("Derrière un NAT") }, supportingContent = { Text(texte(nat)) }) }
                }
                if (diagnostic.keepaliveSecondes != null && diagnostic.inactiviteSecondes != null) {
                    item { ListItem(headlineContent = { Text("Bail") }, supportingContent = { Text("keepalive ${diagnostic.keepaliveSecondes} s, inactivité ${diagnostic.inactiviteSecondes} s") }) }
                }
                item { Aide("Sous quelle adresse il l'a vu — rien d'autre ne le lui apprend — et s'il le croit derrière un NAT, en comparant ce qui est annoncé à ce qu'il observe. « Indéterminé » : le daemon n'a annoncé aucune adresse locale, il n'y avait rien à comparer.") }
            }
            item { SousTitre("Service") }
            item { LigneIdentifiant("Identifiant public", service.id, partageable = true) }
            item { ListItem(headlineContent = { Text("Machine") }, supportingContent = { Text(fiche.nom) }) }
        }
    }
}

@Composable
private fun LignePoint(point: String, verdict: Joignabilite?) {
    ListItem(
        leadingContent = { Pastille(verdict?.couleur ?: Couleurs.parti, 8) },
        headlineContent = { Text(point, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Column {
                if (verdict != null) {
                    Text(verdict.libelle, style = MaterialTheme.typography.labelLarge)
                    Text(verdict.detail)
                    if (verdict is Joignabilite.Joignable && verdict.candidat.isNotEmpty()) {
                        Text("vers ${verdict.candidat}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("Aucun verdict : le service est parti.")
                }
            }
        },
    )
}

private fun adresse(candidat: Candidat): String {
    val hote = if (candidat.adresse.contains(':')) "[${candidat.adresse}]" else candidat.adresse
    return "${candidat.protocole.libelle} $hote:${candidat.port}"
}

private fun texte(nat: Diagnostic.Nat) = when (nat) {
    Diagnostic.Nat.OUI -> "oui"
    Diagnostic.Nat.NON -> "non"
    Diagnostic.Nat.INDETERMINE -> "indéterminé"
}
