package org.airdesktop.servicelocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.airdesktop.servicelocator.identite.EtatIdentite
import org.airdesktop.servicelocator.identite.IdentiteLocale

/**
 * L'unique écran de l'application.
 *
 * Il n'affiche que l'état du dépôt et ce que l'appareil sait confirmer. Une
 * application qui présenterait des écrans vides aurait l'air de fonctionner ;
 * celle-ci dit qu'elle n'a rien à montrer, parce que les spécifications ne sont
 * pas écrites.
 *
 * **La capacité de l'appareil est interrogée dès le lancement, et c'est
 * délibéré.** Un appareil sans biométrie enrôlée ne peut pas porter cette
 * application : le découvrir au moment de la première connexion ferait
 * installer, ouvrir un compte, puis échouer.
 */
class ActivitePrincipale : ComponentActivity() {
    override fun onCreate(etatSauvegarde: Bundle?) {
        super.onCreate(etatSauvegarde)
        val etat = IdentiteLocale(this).etat()
        setContent {
            MaterialTheme {
                EcranProvisoire(etat)
            }
        }
    }
}

@Composable
private fun EcranProvisoire(etat: EtatIdentite) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "air-service-locator",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Ce dépôt porte une arborescence, pas encore une application.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = etat.description,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
