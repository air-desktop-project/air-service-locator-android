package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.identite.EtatIdentite

/**
 * Le premier écran : ouvrir un compte. Pas de mot de passe, pas de formulaire
 * — un geste biométrique, et un identifiant public en retour.
 */
@Composable
fun AccueilEcran() {
    val session = LocalSession.current
    val activite = LocalContext.current as FragmentActivity
    val portee = rememberCoroutineScope()
    val etat = remember { session.identite.etat() }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Logo(88.dp)
            Spacer(Modifier.height(16.dp))
            Text("Service Locator", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Vos machines, leurs daemons, et le port où les joindre.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Argument(Icones.empreinte, "Aucun mot de passe", "Votre compte est cet appareil. Votre empreinte ou votre visage confirme que c'est vous, ici, et rien ne quitte l'appareil.")
                Argument(Icones.cle, "Une clé dans le matériel sécurisé", "Elle signe vos demandes et ne peut pas en sortir.")
                Argument(Icones.oeil, "Aucune donnée personnelle", "Ni courriel, ni numéro, ni nom. Vous recevez un identifiant public, c'est tout.")
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = "Racines air-desktop-project", onValueChange = {}, readOnly = true, enabled = false,
                label = { Text("Annuaire") }, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Vous pourrez désigner votre propre annuaire, ou celui de votre organisation.") },
            )
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (erreur != null) {
                Text(erreur!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    portee.launch {
                        enCours = true
                        try {
                            session.ouvrirCompte(activite)
                        } catch (e: Exception) {
                            erreur = e.messageAnnuaire
                        } finally {
                            enCours = false
                        }
                    }
                },
                enabled = !enCours && etat is EtatIdentite.Disponible,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icones.empreinte, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Ouvrir un compte")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                pied(etat), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Ce qui exclut l'application se dit ici, tout de suite, plutôt qu'au moment de la première connexion. */
private fun pied(etat: EtatIdentite): String = when (etat) {
    EtatIdentite.Disponible -> "Un compte sur un seul appareil est un compte qu'un téléphone perdu ferme. Vous pourrez en enrôler un second."
    EtatIdentite.RienEnrole -> "Aucune empreinte ni visage n'est enrôlé sur cet appareil. Enrôlez-en dans les Paramètres, puis revenez."
    EtatIdentite.Indisponible -> "La biométrie est momentanément indisponible. Réessayez dans un instant."
    is EtatIdentite.Absente -> "Cet appareil ne peut pas confirmer l'identité de son porteur, et cette application ne peut donc pas y ouvrir de compte. ${etat.raison}."
}

@Composable
private fun Argument(icone: ImageVector, titre: String, texte: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column {
            Text(titre, style = MaterialTheme.typography.titleMedium)
            Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** PROVISOIRE : aucun logo « Air » n'existe encore. Une pastille, en attendant. */
@Composable
fun Logo(taille: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(taille).background(Couleurs.accent, RoundedCornerShape(taille * 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icones.machine, null, tint = Color.White, modifier = Modifier.size(taille * 0.5f))
    }
}
