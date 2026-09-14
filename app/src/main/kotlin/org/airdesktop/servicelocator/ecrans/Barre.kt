package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import org.airdesktop.servicelocator.composants.Icones

/** La barre du haut : un titre, un retour si l'écran n'est pas une racine, des actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Barre(titre: String, nav: NavController? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(titre) },
        navigationIcon = {
            if (nav != null) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icones.retour, "Retour") }
            }
        },
        actions = actions,
    )
}
