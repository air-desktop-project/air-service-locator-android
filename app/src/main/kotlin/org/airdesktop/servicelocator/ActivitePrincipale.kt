package org.airdesktop.servicelocator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.composants.ThemeServiceLocator
import org.airdesktop.servicelocator.notifications.Distributeurs
import org.airdesktop.servicelocator.notifications.Nouvelles

/**
 * Le point d'entrée : une fenêtre sur la session, qui, elle, vit dans
 * [ApplicationServiceLocator] — c'est là que l'on choisit qui répond aux
 * écrans, et là que le transport survit à une rotation.
 *
 * `FragmentActivity` et non `ComponentActivity` : `BiometricPrompt` l'exige.
 */
class ActivitePrincipale : FragmentActivity() {
    private val appli: ApplicationServiceLocator get() = application as ApplicationServiceLocator
    private val session: Session get() = appli.session

    override fun onCreate(etatSauvegarde: Bundle?) {
        super.onCreate(etatSauvegarde)
        // À chaque ouverture, comme UnifiedPush le demande : un point changé pendant que l'application dormait revient.
        if (etatSauvegarde == null) Distributeurs.reinscrire(this)
        setContent {
            CompositionLocalProvider(LocalSession provides session) {
                ThemeServiceLocator {
                    Racine()
                }
            }
        }
    }

    /**
     * Revenir au premier plan après une notification, c'est l'ouverture que
     * la notification demandait : on relit. **Seulement alors** — relire à
     * chaque retour ferait redemander une empreinte à qui revient d'une autre
     * application, si la connexion est tombée entre-temps. Au lancement, c'est
     * [Racine] qui relit.
     */
    override fun onResume() {
        super.onResume()
        Nouvelles.retirer(this)
        if (appli.notifications.aRelire && session.compte != null) lifecycleScope.launch { session.relire() }
    }
}
