package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire
import java.time.Instant

/** Le code à taper sur la machine : `asl enrole 4K9M2-P7R1T`. */
@Composable
fun CodeEnrolementEcran(nav: NavController, id: Identifiant) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val presse = LocalClipboardManager.current
    var machine by remember { mutableStateOf<Machine?>(null) }
    var code by remember { mutableStateOf<CodeEnrolement?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var maintenant by remember { mutableStateOf(Instant.now()) }

    suspend fun emettre() {
        runCatching { session.annuaire.emettreCode(id) }
            .onSuccess { code = it; erreur = null }
            .onFailure { erreur = it.messageAnnuaire }
    }

    LaunchedEffect(Unit) {
        val m = runCatching { session.annuaire.machines().firstOrNull { it.id == id } ?: throw ErreurAnnuaire.Introuvable }
            .getOrElse { erreur = it.messageAnnuaire; return@LaunchedEffect }
        machine = m
        // Le code émis à la déclaration est encore bon : on ne le remplace pas
        // pour rien. Un code absent ou expiré, lui, appelle le suivant.
        val existant = when (val cle = m.cle) {
            is Machine.Cle.Attendue -> cle.code
            is Machine.Cle.Revoquee -> cle.code
            is Machine.Cle.Enrolee -> null
        }
        if (existant != null && existant.estValide(Instant.now())) code = existant else emettre()
        while (true) { delay(1_000); maintenant = Instant.now() }
    }

    Scaffold(topBar = { Barre("Enrôler ${machine?.nom ?: ""}", nav) }) { marges ->
        Column(
            Modifier.fillMaxSize().padding(marges).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            val c = code
            if (c != null) {
                val valide = c.estValide(maintenant)
                // Onze caractères sur une ligne, quelle que soit la taille d'affichage : un
                // code replié sur deux lignes se recopie mal, et se recopier est son seul emploi.
                Text(
                    c.texteGroupe, fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    maxLines = 1, softWrap = false,
                    color = if (valide) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icones.horloge, null, Modifier.width(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (valide) "Valable encore ${Formats.minutesSecondes(c.reste(maintenant))}" else "Expiré",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sur la machine, tapez :", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icones.terminal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.commande, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { presse.setText(AnnotatedString(c.commande)) }) {
                            Icon(Icones.copier, "Copier la commande", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Le code ne sert qu'une fois et n'ouvre qu'une seule opération : lier la clé que la machine génère sur place à ce compte. La clé privée ne quitte jamais la machine.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            Erreur(erreur)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { portee.launch { emettre() } }, modifier = Modifier.fillMaxWidth()) { Text("Émettre un nouveau code") }
            Spacer(Modifier.height(8.dp))
            Text("Le code précédent meurt à l'émission du suivant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}
