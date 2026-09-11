package org.airdesktop.servicelocator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.composants.ThemeServiceLocator
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.reseau.AnnuaireSimule
import org.airdesktop.servicelocator.reseau.Demonstration

/**
 * Le point d'entrée.
 *
 * **L'annuaire est simulé** ([AnnuaireSimule]) tant que le transport de
 * `asl-client` n'est pas embarqué : les écrans parlent à l'interface
 * `Annuaire`, et c'est ici, et nulle part ailleurs, que l'on choisit qui
 * répond. Le jour où le client Rust arrive, cette composition change ; les
 * écrans, non.
 *
 * `FragmentActivity` et non `ComponentActivity` : `BiometricPrompt` l'exige.
 */
class ActivitePrincipale : FragmentActivity() {
    private val session: Session by lazy {
        val simule = AnnuaireSimule()
        Session(simule, IdentiteLocale(this)) { Demonstration.ouvrirCompte(simule) }
    }

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
