package org.airdesktop.servicelocator.composants

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.Service
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire

/** Un point de couleur : ce que l'on met devant une machine ou un service. */
@Composable
fun Pastille(couleur: Color, taille: Int = 10) {
    Box(Modifier.size(taille.dp).background(couleur, CircleShape))
}

/** Le sous-titre de section de Material 3, en couleur primaire. */
@Composable
fun SousTitre(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Une phrase d'aide sous une liste ou un champ. */
@Composable
fun Aide(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun Erreur(texte: String?) {
    if (texte != null) {
        Text(texte, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/** Une ligne « identifiant à copier », et à partager si demandé. */
@Composable
fun LigneIdentifiant(titre: String, identifiant: Identifiant, partageable: Boolean = false) {
    val presse = LocalClipboardManager.current
    val contexte = LocalContext.current
    ListItem(
        overlineContent = { Text(titre) },
        headlineContent = { Text(identifiant.texte, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Row {
                IconButton(onClick = { presse.setText(AnnotatedString(identifiant.texte)) }) {
                    Icon(Icones.copier, "Copier l'identifiant", tint = MaterialTheme.colorScheme.primary)
                }
                if (partageable) {
                    IconButton(onClick = {
                        val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, identifiant.texte)
                        contexte.startActivity(Intent.createChooser(envoi, null))
                    }) {
                        Icon(Icones.partager, "Partager l'identifiant", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    )
}

/** Les deux cases, avec ce que chacune ouvre. Rien n'est coché d'avance. */
@Composable
fun ChoixCapacites(capacites: Set<Capacite>, surChangement: (Set<Capacite>) -> Unit) {
    Column {
        Case("Annonce", "Ses daemons peuvent annoncer leurs ports.", Capacite.ANNONCE in capacites) {
            surChangement(if (it) capacites + Capacite.ANNONCE else capacites - Capacite.ANNONCE)
        }
        Case("Lecture", "Elle peut demander où joindre un service — les vôtres, et ceux qu'on vous a accordés.", Capacite.LECTURE in capacites) {
            surChangement(if (it) capacites + Capacite.LECTURE else capacites - Capacite.LECTURE)
        }
    }
}

@Composable
private fun Case(titre: String, sous: String, coche: Boolean, surChangement: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = coche, onCheckedChange = surChangement)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.padding(top = 12.dp)) {
            Text(titre, style = MaterialTheme.typography.bodyLarge)
            Text(sous, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ce que l'on charge depuis l'annuaire, et de quoi le recharger. */
class Chargement<T>(val valeur: T?, val erreur: String?, val recharger: () -> Unit)

/** Charge une valeur depuis l'annuaire à l'affichage, et à chaque `recharger()`. */
@Composable
fun <T> rememberChargement(vararg cles: Any?, charger: suspend () -> T): Chargement<T> {
    var valeur by remember { mutableStateOf<T?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var tour by remember { mutableIntStateOf(0) }
    LaunchedEffect(tour, *cles) {
        runCatching { charger() }
            .onSuccess { valeur = it; erreur = null }
            .onFailure { erreur = it.messageAnnuaire }
    }
    return Chargement(valeur, erreur) { tour++ }
}

/** Une erreur d'annuaire, dite à l'utilisateur dans ses mots. */
val Throwable.messageAnnuaire: String
    get() = if (this is ErreurAnnuaire) message ?: "Refusé." else message ?: toString()

// ── Le vocabulaire des états ─────────────────────────────────────────────────

/** Le mot, exactement — et jamais « en ligne ». */
val Joignabilite.libelle: String
    get() = when (this) {
        Joignabilite.EnCours -> "Annoncé"
        is Joignabilite.Joignable -> "Joignable"
        is Joignabilite.Injoignable -> "Annoncé, injoignable"
        Joignabilite.NonSonde -> "Annoncé"
    }

/** « joignable » porte toujours sa date : un « joignable » sans date décrit le passé au présent. */
val Joignabilite.detail: String
    get() = when (this) {
        Joignabilite.EnCours -> "sonde en cours"
        is Joignabilite.Joignable -> "depuis l'annuaire, ${Formats.relatif(depuis)}"
        is Joignabilite.Injoignable -> "depuis l'annuaire, ${Formats.relatif(depuis)}"
        Joignabilite.NonSonde -> "UDP — non sondé"
    }

val Joignabilite.couleur: Color
    get() = when (this) {
        Joignabilite.EnCours -> Couleurs.accent
        is Joignabilite.Joignable -> Couleurs.joignable
        is Joignabilite.Injoignable -> Couleurs.attention
        Joignabilite.NonSonde -> Color(0xFFC3C6CF)
    }

val Service.couleur: Color
    get() = when (etat) {
        is Service.Etat.Annonce -> resume?.couleur ?: Couleurs.accent
        is Service.Etat.Parti -> Couleurs.parti
    }

val Service.libelleEtat: String
    get() = when (etat) {
        is Service.Etat.Annonce -> resume?.libelle ?: "Annoncé"
        is Service.Etat.Parti -> "Parti"
    }

val Service.detailEtat: String
    get() = when (val e = etat) {
        is Service.Etat.Annonce -> resume?.detail ?: ""
        // La date n'est connue que si l'on a vu le départ, et le motif pas toujours : l'annuaire n'en range ni l'un ni l'autre.
        is Service.Etat.Parti -> listOfNotNull(
            e.volontaire?.let { if (it) "arrêt volontaire" else "inactivité" } ?: "motif inconnu",
            e.le?.let { Formats.relatif(it) },
        ).joinToString(", ")
    }

/** Le point dit si la machine tient une connexion à l'annuaire. Il ne dit pas qu'un service est joignable. */
val Machine.couleur: Color
    get() = when {
        unServiceOscille -> Couleurs.attention
        services.any { it.etat is Service.Etat.Annonce } -> Couleurs.joignable
        else -> Couleurs.parti
    }

val Machine.resumeListe: String
    get() {
        val morceaux = mutableListOf(capacitesTexte.ifEmpty { "aucune capacité" })
        val vivants = services.count { it.etat is Service.Etat.Annonce }
        if (vivants > 0) morceaux += "$vivants service${if (vivants > 1) "s" else ""}"
        if (unServiceOscille) morceaux += "un service oscille"
        return morceaux.joinToString(" · ")
    }
