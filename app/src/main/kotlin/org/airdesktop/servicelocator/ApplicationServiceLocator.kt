package org.airdesktop.servicelocator

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.identite.CleAppareil
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.notifications.CarnetNotifications
import org.airdesktop.servicelocator.notifications.Nouvelles
import org.airdesktop.servicelocator.reseau.AnnuaireReel
import org.airdesktop.servicelocator.reseau.AnnuaireSimule
import org.airdesktop.servicelocator.reseau.ChoixDAnnuaire
import org.airdesktop.servicelocator.reseau.Demonstration
import org.airdesktop.servicelocator.reseau.ListeDAnnuaires
import org.airdesktop.servicelocator.reseau.MemoireDuChoix

/**
 * Le processus, et ce qui doit vivre aussi longtemps que lui.
 *
 * **La session vit ici, et pas dans l'activité.** Une activité meurt à chaque
 * rotation d'écran ; si la session mourait avec elle, le transport tenu
 * tomberait, se rouvrirait, et redemanderait une empreinte — pour avoir
 * tourné le téléphone. Un geste par connexion, pas par rotation.
 *
 * **C'est ici, et nulle part ailleurs, que l'on choisit qui répond** aux
 * écrans. Si `local.properties` a donné des annuaires (leur liste et leur
 * racine), c'est le transport réel, vers celle que l'utilisateur a choisie
 * ([ChoixDAnnuaire]) ; sinon, le banc en mémoire, peuplé de démonstration.
 * Les écrans ne voient que l'interface `Annuaire`.
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

    /**
     * Ce que cet appareil retient des notifications. **Hors de la session** : le
     * récepteur UnifiedPush l'écrit dans un processus que le distributeur vient
     * de réveiller, sans construire d'annuaire ni charger le transport.
     */
    val notifications: CarnetNotifications by lazy { CarnetNotifications(this) }

    val session: Session by lazy {
        val identite = IdentiteLocale(this)
        val racines = ListeDAnnuaires.lire(BuildConfig.ANNUAIRES)
        if (racines.isNotEmpty() && BuildConfig.ANNUAIRE_RACINES.isNotEmpty()) {
            val choix = ChoixDAnnuaire(racines, MemoireDuChoixPartagee(this)) { racine ->
                AnnuaireReel(
                    this,
                    AnnuaireReel.Reglages(racine.adresse, racine.nom, BuildConfig.ANNUAIRE_RACINES.toByteArray()),
                    signataire = { defi -> CleAppareil.ouOuvrir(defi).avec { activiteAuPremierPlan } },
                    cleExiste = { CleAppareil.existe() },
                    effacerCle = { CleAppareil.effacer() },
                    racinesConnues = racines,
                )
            }
            Session(choix.annuaire, identite, notifications, choix = choix) { invitation, _ -> choix.annuaire.ouvrirCompte(invitation) }
        } else {
            val simule = AnnuaireSimule()
            Session(simule, identite, notifications) { invitation, signataire -> Demonstration.ouvrirCompte(simule, signataire(), invitation) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Nouvelles.creerCanal(this)
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

/**
 * Le choix de la racine, retenu dans les préférences de l'application — sous
 * la clé `annuaire.adresse`, la même que l'application iOS. Par application,
 * pas par compte : le compte existe sur chaque racine.
 */
private class MemoireDuChoixPartagee(contexte: Context) : MemoireDuChoix {
    private val prefs = contexte.getSharedPreferences("annuaire", Context.MODE_PRIVATE)
    override var adresse: String?
        get() = prefs.getString("annuaire.adresse", null)
        set(valeur) = prefs.edit().putString("annuaire.adresse", valeur).apply()
}
