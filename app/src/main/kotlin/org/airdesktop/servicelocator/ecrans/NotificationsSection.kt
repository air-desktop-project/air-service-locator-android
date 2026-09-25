package org.airdesktop.servicelocator.ecrans

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.notifications.Distributeurs
import org.airdesktop.servicelocator.notifications.Nouvelles
import org.airdesktop.servicelocator.reseau.EtatNotifications

/**
 * Compte › Notifications : où elles en sont, et le geste pour les activer ou
 * les couper.
 *
 * **Le moment de demander la permission est celui où l'utilisateur les
 * active** — pas le lancement, où « autoriser les notifications ? » ne dirait
 * pas pourquoi. Refusée, rien ne casse : le distributeur réveille toujours
 * l'application, rien ne s'affiche, et la relecture à l'ouverture montre ce qui
 * a été accordé.
 */
@Composable
fun SectionNotifications() {
    val contexte = LocalContext.current
    val session = LocalSession.current
    val carnet = session.notifications
    // L'état se relit à chaque retour au premier plan : on revient d'installer un distributeur, ou des réglages.
    var retour by remember { mutableIntStateOf(0) }
    val cycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cycle) {
        val observateur = LifecycleEventObserver { _, evenement -> if (evenement == Lifecycle.Event.ON_RESUME) retour++ }
        cycle.addObserver(observateur)
        onDispose { cycle.removeObserver(observateur) }
    }
    val compte = session.compte?.identifiant
    val etat = remember(retour, carnet.tour, compte) {
        EtatNotifications.de(
            Distributeurs.installes(contexte), Distributeurs.retenu(contexte), carnet.point,
            compte?.let { carnet.depose(it) }, carnet.refus,
        )
    }
    val permises = remember(retour, carnet.tour) { Nouvelles.permises(contexte) }
    var aChoisir by remember { mutableStateOf<List<String>?>(null) }
    // Le distributeur à activer une fois la permission répondue — accordée ou non, on l'active : le refus ne coupe
    // que l'affichage, la relecture et le dépôt du point restent utiles.
    var enAttenteDePermission by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enAttenteDePermission?.let { Distributeurs.activer(contexte, it) }
        enAttenteDePermission = null
        retour++
    }

    fun activer(paquet: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Nouvelles.permises(contexte)) {
            enAttenteDePermission = paquet
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Distributeurs.activer(contexte, paquet)
            retour++
        }
    }

    Column {
        when (etat) {
            EtatNotifications.SansDistributeur -> {
                ListItem(
                    leadingContent = { Icon(Icones.cloche, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text("Aucun distributeur UnifiedPush") },
                    supportingContent = { Text("Pas de notification sur ce téléphone. Ce qu'on vous accorde s'affiche quand vous ouvrez l'application.") },
                )
                Aide("Pour être prévenu sans passer par Google ni Apple, installez le distributeur UnifiedPush de votre choix — ntfy, par exemple, depuis F-Droid —, puis revenez ici. L'application n'en impose aucun.")
            }
            is EtatNotifications.AChoisir -> {
                ListItem(
                    leadingContent = { Icon(Icones.cloche, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text("Notifications désactivées") },
                    supportingContent = {
                        Text(etat.distributeurs.joinToString(", ") { Distributeurs.libelle(contexte, it) }.let { "Distributeur disponible : $it" })
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            if (etat.distributeurs.size == 1) activer(etat.distributeurs.single()) else aChoisir = etat.distributeurs
                        }) { Text("Activer") }
                    },
                )
                Aide("Quand quelqu'un vous accorde un accès, l'annuaire envoie à votre distributeur un message vide ; l'application affiche « Du nouveau dans Service Locator », et le détail à l'ouverture — la clé de cet appareil ne s'emploie qu'après votre empreinte.")
            }
            is EtatNotifications.EnAttente -> ListItem(
                leadingContent = { Icon(Icones.cloche, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                headlineContent = { Text("En attente de ${Distributeurs.libelle(contexte, etat.distributeur)}") },
                supportingContent = { Text("Le distributeur n'a pas encore donné de point.") },
                trailingContent = { TextButton(onClick = { Distributeurs.desactiver(contexte); carnet.oublierPoint() }) { Text("Annuler") } },
            )
            is EtatNotifications.Actif -> {
                ListItem(
                    leadingContent = { Icon(Icones.cloche, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Activées, par ${Distributeurs.libelle(contexte, etat.distributeur)}") },
                    supportingContent = {
                        when {
                            etat.depose -> Text("L'annuaire a le point de cet appareil.")
                            etat.refus != null -> Text("L'annuaire refuse le point de ce distributeur : ${etat.refus}.", color = MaterialTheme.colorScheme.error)
                            else -> Text("Le point partira vers l'annuaire à la prochaine connexion.")
                        }
                    },
                    trailingContent = { TextButton(onClick = { Distributeurs.desactiver(contexte); carnet.oublierPoint() }) { Text("Désactiver") } },
                )
                Aide("Désactiver se fait auprès du distributeur : l'annuaire n'a pas de verbe pour retirer un point, il apprend au premier envoi que celui-ci est mort.")
            }
        }
        val inscrit = etat is EtatNotifications.Actif || etat is EtatNotifications.EnAttente
        if (inscrit && !permises) {
            ListItem(
                headlineContent = { Text("Android n'affiche pas les notifications de l'application") },
                supportingContent = { Text("Le distributeur la réveille, mais rien ne s'affiche. Les nouveaux accès restent visibles à l'ouverture.") },
                modifier = Modifier.clickable {
                    contexte.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexte.packageName))
                },
                trailingContent = { Icon(Icones.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }

    aChoisir?.let { distributeurs ->
        AlertDialog(
            onDismissRequest = { aChoisir = null },
            title = { Text("Quel distributeur ?") },
            text = {
                Column {
                    for (paquet in distributeurs) {
                        ListItem(
                            headlineContent = { Text(Distributeurs.libelle(contexte, paquet)) },
                            supportingContent = { Text(paquet) },
                            modifier = Modifier.clickable { aChoisir = null; activer(paquet) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { aChoisir = null }) { Text("Annuler") } },
        )
    }
}
