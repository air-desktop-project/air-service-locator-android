package org.airdesktop.servicelocator.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.airdesktop.servicelocator.BuildConfig

/**
 * L'écran de capture d'un jeton Play Integrity — variante de débogage
 * seulement, sans lanceur, ouvert par `adb`.
 *
 * Il fait une chose : appeler `capturerUnJeton` avec le numéro de projet lu de
 * `local.properties`, et afficher le bloc que Logcat reçoit aussi, pour qu'on
 * puisse le copier sans chercher dans le journal.
 */
class ActiviteCapture : ComponentActivity() {
    override fun onCreate(etatSauvegarde: Bundle?) {
        super.onCreate(etatSauvegarde)
        setContent {
            MaterialTheme {
                var rendu by remember { mutableStateOf("Projet Cloud : ${BuildConfig.NUMERO_PROJET_CLOUD}") }
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("Capture Play Integrity", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { capturerUnJeton(this@ActiviteCapture, BuildConfig.NUMERO_PROJET_CLOUD) { rendu = it } }) {
                        Text("Demander un jeton")
                    }
                    Text(rendu, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
