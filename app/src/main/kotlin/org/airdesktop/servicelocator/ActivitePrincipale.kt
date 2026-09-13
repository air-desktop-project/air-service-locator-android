package org.airdesktop.servicelocator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.composants.ThemeServiceLocator

/**
 * Le point d'entrée : une fenêtre sur la session, qui, elle, vit dans
 * [ApplicationServiceLocator] — c'est là que l'on choisit qui répond aux
 * écrans, et là que le transport survit à une rotation.
 *
 * `FragmentActivity` et non `ComponentActivity` : `BiometricPrompt` l'exige.
 */
class ActivitePrincipale : FragmentActivity() {
    private val session: Session get() = (application as ApplicationServiceLocator).session

    override fun onCreate(etatSauvegarde: Bundle?) {
        super.onCreate(etatSauvegarde)
        setContent {
            CompositionLocalProvider(LocalSession provides session) {
                ThemeServiceLocator {
                    Racine()
                }
            }
        }
    }
}
