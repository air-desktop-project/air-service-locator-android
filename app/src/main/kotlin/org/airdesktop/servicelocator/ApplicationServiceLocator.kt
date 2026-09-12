package org.airdesktop.servicelocator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.identite.CleAppareil
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.reseau.AnnuaireReel
import org.airdesktop.servicelocator.reseau.AnnuaireSimule
import org.airdesktop.servicelocator.reseau.Demonstration

/**
 * Le processus, et ce qui doit vivre aussi longtemps que lui.
 *
 * **La session vit ici, et pas dans l'activité.** Une activité meurt à chaque
 * rotation d'écran ; si la session mourait avec elle, le transport tenu
 * tomberait, se rouvrirait, et redemanderait une empreinte — pour avoir
 * tourné le téléphone. Un geste par connexion, pas par rotation.
 *
 * **C'est ici, et nulle part ailleurs, que l'on choisit qui répond** aux
 * écrans. Si `local.properties` a donné un annuaire (adresse, nom, racine),
 * c'est le transport réel ; sinon, le banc en mémoire, peuplé de
 * démonstration. Les écrans ne voient que l'interface `Annuaire`.
 */
class ApplicationServiceLocator : Application() {
    /**
     * L'activité devant le porteur, s'il y en a une. C'est elle qui affiche
     * `BiometricPrompt` quand le transport, de son propre fil, demande une
     * signature — et ce doit être celle d'aujourd'hui, pas celle d'avant la
     * rotation.
     */
    @Volatile
    var activiteAuPremierPlan: FragmentActivity? = null
        private set

    val session: Session by lazy {
        val identite = IdentiteLocale(this)
        if (BuildConfig.ANNUAIRE_ADRESSE.isNotEmpty() && BuildConfig.ANNUAIRE_RACINES.isNotEmpty()) {
            val reglages = AnnuaireReel.Reglages(BuildConfig.ANNUAIRE_ADRESSE, BuildConfig.ANNUAIRE_NOM, BuildConfig.ANNUAIRE_RACINES.toByteArray())
            val reel = AnnuaireReel(this, reglages) { CleAppareil.ouOuvrir().avec { activiteAuPremierPlan } }
            Session(reel, identite) { signataire -> reel.ouvrirCompte(signataire) }
        } else {
            val simule = AnnuaireSimule()
            Session(simule, identite) { signataire -> Demonstration.ouvrirCompte(simule, signataire) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activiteAuPremierPlan = activity as? FragmentActivity
            }

            override fun onActivityPaused(activity: Activity) {
                if (activiteAuPremierPlan === activity) activiteAuPremierPlan = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
