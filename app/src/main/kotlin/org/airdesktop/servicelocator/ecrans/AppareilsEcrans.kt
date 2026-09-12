package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.CadreQR
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneCopiable
import org.airdesktop.servicelocator.composants.ReceptionInvitation
import org.airdesktop.servicelocator.composants.SousTitre
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.modele.Invitation

/**
 * `POST /v1/appareils`, **depuis l'appareil déjà enrôlé** : lire la clé que le
 * nouveau téléphone montre, la poster, et lui rendre l'invitation.
 *
 * Deux temps sur un même écran, parce que l'utilisateur tient les deux
 * téléphones : d'abord lire, puis montrer. Rien de secret ne s'affiche — une
 * clé publique, deux identifiants — et c'est le nouveau téléphone qui devra
 * prouver sa clé, sur sa propre connexion.
 */
@Composable
fun EnrolerAppareilEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var reponse by remember { mutableStateOf<Invitation.Appareil?>(null) }

    Scaffold(topBar = { Barre("Enrôler un appareil", nav) }) { marges ->
        Column(Modifier.fillMaxSize().padding(marges).verticalScroll(rememberScrollState())) {
            val rendue = reponse
            if (rendue != null) {
                SousTitre("L'appareil est enrôlé")
                Spacer(Modifier.height(8.dp))
                CadreQR(rendue.texte)
                Text(
                    "Sur le nouveau téléphone, lisez ce code en retour. Il rejoindra le compte en prouvant sa clé — un geste, là-bas.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                LigneCopiable("Ou envoyez-lui ce texte", rendue.texte)
                Aide("Il apparaît dans la liste des appareils. Tant qu'il n'a pas lu ce code, il ne peut rien faire ; un code lu par un autre téléphone ne lui sert à rien sans la clé.")
            } else {
                Aide("Sur le nouveau téléphone, ouvrez l'application et choisissez « Rejoindre un compte existant ». Il affiche un code : lisez-le ici.")
                ReceptionInvitation("Le code commence par « asl:cle: » et porte la clé publique du nouveau téléphone — rien de secret.") { invitation ->
                    if (invitation !is Invitation.Cle) {
                        erreur = "Ce code est une réponse, pas une clé : c'est l'autre téléphone qui doit le lire."
                        return@ReceptionInvitation
                    }
                    val compte = session.compte ?: return@ReceptionInvitation
                    portee.launch {
                        enCours = true
                        try {
                            val appareil = session.annuaire.enrolerAppareil(invitation.octets)
                            reponse = Invitation.Appareil(compte.identifiant, appareil.id)
                            erreur = null
                        } catch (e: Exception) {
                            erreur = e.messageAnnuaire
                        } finally {
                            enCours = false
                        }
                    }
                }
                if (enCours) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
                Erreur(erreur)
            }
        }
    }
}

/**
 * Rejoindre un compte existant, **depuis le nouveau téléphone** : montrer sa
 * clé, puis lire l'invitation que l'autre téléphone rend.
 *
 * La clé est née ici, dans le Keystore, à la première ouverture de cet écran ;
 * la montrer ne demande aucun geste. Le geste vient à la fin, quand
 * l'invitation est lue : c'est la preuve, sur cette connexion, que la clé
 * enrôlée là-bas est bien celle d'ici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RejoindreEcran(retour: () -> Unit) {
    val session = LocalSession.current
    val activite = LocalContext.current as FragmentActivity
    val portee = rememberCoroutineScope()
    var cle by remember { mutableStateOf<ByteArray?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { session.clePublique(activite) }
            .onSuccess { cle = it }
            .onFailure { erreur = "La clé de cet appareil n'a pas pu être créée : ${it.message}" }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Rejoindre un compte") }, navigationIcon = { IconButton(onClick = retour) { Icon(Icones.retour, "Retour") } })
    }) { marges ->
        Column(Modifier.fillMaxSize().padding(marges).verticalScroll(rememberScrollState())) {
            val montree = cle?.let { Invitation.Cle(it) }
            if (montree != null) {
                SousTitre("1. Montrez la clé de cet appareil")
                Spacer(Modifier.height(8.dp))
                CadreQR(montree.texte)
                Text(
                    "Sur le téléphone déjà enrôlé : Compte › Enrôler un autre appareil, puis lisez ce code.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                LigneCopiable("Ou envoyez-lui ce texte", montree.texte)
                Aide("C'est une clé publique : la montrer ne donne rien à personne. Sa moitié secrète ne quitte pas le matériel sécurisé.")
                SousTitre("2. Lisez sa réponse")
                ReceptionInvitation("Le code commence par « asl:appareil: ». Le lire demande un geste : la clé de cet appareil prouve qu'elle est bien celle qui vient d'être enrôlée.") { invitation ->
                    if (invitation !is Invitation.Appareil) {
                        erreur = "Ce code est une clé, pas une réponse : c'est l'autre téléphone qui doit le lire."
                        return@ReceptionInvitation
                    }
                    portee.launch {
                        enCours = true
                        try {
                            session.rejoindre(activite, invitation.compte, invitation.appareil)
                        } catch (e: Exception) {
                            erreur = e.messageAnnuaire
                        } finally {
                            enCours = false
                        }
                    }
                }
                if (enCours) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Erreur(erreur)
        }
    }
}
