package org.airdesktop.servicelocator.composants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ── La règle d'écran du Mac, portée au téléphone ─────────────────────────────
//
// Validée par Thierry sur l'application Mac (PR iOS #36, `Sources/Mac/Composants.swift`) : **on lit en tuiles** — un
// objet de la liste, tout entier lisible ; **chaque donnée qui se change a son bouton en regard** ; **toute saisie
// passe par une feuille** (ici un `ModalBottomSheet`), avec Annuler et l'action ; **les gestes qu'on ne défait pas
// sont à part, en bas, en rouge**, précédés de la phrase qui dit ce qu'ils font. Les mêmes morceaux qu'au Mac, sous
// les mêmes noms, pour qu'une page se relise d'une plate-forme à l'autre.
//
// Ce qui change au téléphone : la largeur. Le libellé passe AU-DESSUS de sa valeur au lieu d'une colonne à gauche,
// et le geste reste à droite, en regard — c'est lui qui compte.

/** Une tuile : un objet de la page (un annuaire, une demande), tout entier lisible, avec ses gestes. */
@Composable
fun Tuile(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenu)
    }
}

/** Le titre d'une tuile, et son état en badge à droite. */
@Composable
fun TeteDeTuile(titre: String, badge: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        badge?.invoke()
    }
}

/**
 * Une ligne de tuile : le libellé, la valeur ENTIÈRE — elle se replie, elle ne se tronque pas —, et le geste qui porte
 * sur elle, en regard. Sans geste, la ligne se lit seulement.
 */
@Composable
fun LigneAGeste(libelle: String, geste: (@Composable () -> Unit)? = null, valeur: @Composable ColumnScope.() -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(libelle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            valeur()
        }
        if (geste != null) {
            Spacer(Modifier.width(8.dp))
            geste()
        }
    }
}

/** Un identifiant ou une adresse : en police fixe, entier. */
@Composable
fun TexteFixe(texte: String, secondaire: Boolean = false) {
    Text(
        texte,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        color = if (secondaire) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** Une valeur absente, dite en gris : « aucun », « aucun — la paire n'a pas de secours ». */
@Composable
fun TexteAbsent(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Une explication sous les lignes d'une tuile. */
@Composable
fun NoteDeTuile(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Un état, une étiquette : « Acceptée », « En attente ». [couleur] est la teinte de l'état, celle du fond ; **le mot,
 * lui, s'écrit à l'encre** ([Couleurs.Texte]) — un « Vivant » en `#28C840` sur pastille pâle ne se lirait pas.
 */
@Composable
fun Badge(texte: String, couleur: Color) {
    Surface(shape = RoundedCornerShape(50), color = couleur.copy(alpha = 0.15f), contentColor = Couleurs.Texte.encre(couleur)) {
        Text(texte, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

/** Un vrai bouton « Copier », pour ce qui se recopie ailleurs — une commande, un identifiant, une adresse. */
@Composable
fun BoutonCopier(texte: String) {
    val presse = LocalClipboardManager.current
    TextButton(onClick = { presse.setText(AnnotatedString(texte)) }) { Text("Copier") }
}

/** Un geste qu'on ne défait pas : un vrai bouton, bordé et écrit en rouge. Éteint, il se grise comme les autres. */
@Composable
fun BoutonDestructif(titre: String, actif: Boolean = true, action: () -> Unit) {
    val rouge = if (actif) Couleurs.Texte.alerte else MaterialTheme.colorScheme.outlineVariant
    OutlinedButton(
        onClick = action,
        enabled = actif,
        border = BorderStroke(1.dp, rouge),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = rouge),
    ) { Text(titre) }
}

/**
 * Le geste qu'on ne défait pas, à part en bas de la tuile ou de la page : ce qu'il fait, dit en une phrase, et le
 * bouton rouge. [actif] l'éteint le temps que l'annuaire réponde — un second appui n'enverrait rien de plus sûr.
 */
@Composable
fun PiedDestructif(explication: String, titre: String, modifier: Modifier = Modifier, actif: Boolean = true, action: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        Text(explication, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            BoutonDestructif(titre, actif, action)
        }
    }
}

/** Le bouton en regard d'une donnée : « Modifier… », « Changer… », « Choisir… ». Un bouton de texte, discret. */
@Composable
fun BoutonDeGeste(titre: String, action: () -> Unit) {
    TextButton(onClick = action) { Text(titre) }
}

/**
 * Une feuille, montée du bas. **Toute saisie passe par elle** : rien ne s'édite en ligne dans une tuile. Elle s'ouvre
 * dépliée — une feuille à moitié montée cacherait ses boutons derrière le clavier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Feuille(onFermer: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenu,
        )
    }
}

/**
 * Une feuille de saisie : un titre, une explication, le formulaire, l'erreur s'il y en a une — dite DANS la feuille,
 * là où l'on regarde —, et les boutons en bas : Annuler, et l'action.
 *
 * [gauche], s'il y en a un, est le geste qui retire ce que la feuille édite (« Retirer l'alias ») — à gauche des
 * boutons sur le Mac. Le téléphone n'a pas la largeur de trois boutons sur une ligne : il passe SOUS eux, à gauche,
 * à part de l'action, là où un doigt qui vise « Enregistrer » ne le touche pas.
 */
@Composable
fun FeuilleDeSaisie(
    titre: String,
    explication: String?,
    action: String,
    actionPermise: Boolean,
    enCours: Boolean,
    erreur: String?,
    onFermer: () -> Unit,
    valider: () -> Unit,
    gauche: (@Composable () -> Unit)? = null,
    formulaire: @Composable ColumnScope.() -> Unit,
) {
    Feuille(onFermer) {
        Text(titre, style = MaterialTheme.typography.titleLarge)
        if (explication != null) Text(explication, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        formulaire()
        if (erreur != null) Text(erreur, style = MaterialTheme.typography.bodyMedium, color = Couleurs.Texte.alerte)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (enCours) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = onFermer) { Text("Annuler") }
            Button(onClick = valider, enabled = actionPermise && !enCours) { Text(action) }
        }
        if (gauche != null) Row(Modifier.fillMaxWidth()) { gauche() }
    }
}

/**
 * Ce qu'une feuille fait de son action, comme le `faire` des feuilles du Mac : l'attente, puis la feuille se ferme si
 * l'annuaire a rangé le geste ([apres]), ou dit son refus DANS la feuille, qui reste ouverte avec la saisie.
 */
@Stable
class GesteEnFeuille internal constructor(private val portee: CoroutineScope, private val apres: State<() -> Unit>) {
    var enCours by mutableStateOf(false)
        private set
    var erreur by mutableStateOf<String?>(null)
        private set

    fun faire(geste: suspend () -> Unit) {
        if (enCours) return
        enCours = true
        portee.launch {
            runCatching { geste() }
                .onSuccess { erreur = null; apres.value() }
                .onFailure { erreur = it.messageAnnuaire }
            enCours = false
        }
    }
}

/** Le [GesteEnFeuille] d'une feuille ; [apres] est appelé quand l'annuaire a rangé le geste. */
@Composable
fun rememberGesteEnFeuille(apres: () -> Unit): GesteEnFeuille {
    val portee = rememberCoroutineScope()
    val suite = rememberUpdatedState(apres)
    return remember { GesteEnFeuille(portee, suite) }
}
