package org.airdesktop.servicelocator.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.airdesktop.servicelocator.ActivitePrincipale
import org.airdesktop.servicelocator.R
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Les distributeurs UnifiedPush — ce que l'utilisateur a installé, et ce qu'il
 * a choisi. **L'application n'en installe ni n'en impose aucun** : sans
 * distributeur, elle le dit et la relecture à l'ouverture fait le travail.
 *
 * Le connecteur tient lui-même le choix et l'inscription (ses propres
 * préférences) ; ce qui est ici ne fait que l'appeler, sous l'instance par
 * défaut — une seule inscription par application.
 */
object Distributeurs {
    /** Les paquets qui se déclarent distributeurs, trouvés par l'intention `REGISTER` que le connecteur déclare dans ses `<queries>`. */
    fun installes(contexte: Context): List<String> = UnifiedPush.getDistributors(contexte)

    /** Celui que l'utilisateur a choisi, s'il l'est encore. */
    fun retenu(contexte: Context): String? = UnifiedPush.getSavedDistributor(contexte)

    /** Le nom qu'Android donne à ce paquet — c'est lui que l'utilisateur a installé, pas un identifiant. */
    fun libelle(contexte: Context, paquet: String): String = runCatching {
        val pm = contexte.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(paquet, 0)).toString()
    }.getOrDefault(paquet)

    /** Choisit ce distributeur et s'y inscrit ; le point arrive plus tard, par `onNewEndpoint`. */
    fun activer(contexte: Context, paquet: String) {
        UnifiedPush.saveDistributor(contexte, paquet)
        UnifiedPush.registerApp(contexte)
    }

    /**
     * Se désinscrit auprès du distributeur. **L'annuaire n'en est pas prévenu**
     * — il n'a pas de verbe pour cela (`protocole.md` §2.2) : le point meurt, et
     * il l'apprend au premier envoi, qui échoue.
     */
    fun desactiver(contexte: Context) = UnifiedPush.unregisterApp(contexte)

    /**
     * À chaque lancement, si un distributeur est choisi : c'est ce que
     * UnifiedPush demande, pour qu'un point changé pendant que l'application
     * dormait revienne par `onNewEndpoint`. Un distributeur désinstallé depuis
     * n'est plus choisi, et rien ne part.
     */
    fun reinscrire(contexte: Context) {
        val retenu = retenu(contexte) ?: return
        if (retenu in installes(contexte)) UnifiedPush.registerApp(contexte)
    }
}

/**
 * La notification locale, générique. **Elle ne dit ni qui ni quoi**, et elle ne
 * le peut pas : le message de l'annuaire est vide, et la clé de l'appareil ne
 * s'emploie qu'après un geste biométrique — un téléphone réveillé ne peut pas
 * se connecter pour lire. Le détail s'affiche à l'ouverture.
 */
object Nouvelles {
    private const val CANAL = "nouvelles"
    /** Un seul identifiant : trois accès accordés dans la nuit font une notification, pas trois. */
    private const val IDENTIFIANT = 1

    fun creerCanal(contexte: Context) {
        val canal = NotificationChannel(CANAL, "Nouveaux accès", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quelqu'un vous a accordé un accès. Le détail s'affiche à l'ouverture de l'application."
        }
        contexte.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /** Sous Android 13 et plus, sans la permission, rien ne s'affiche — et la relecture à l'ouverture reste. */
    fun permises(contexte: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            contexte.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun montrer(contexte: Context) {
        if (!permises(contexte)) return
        val ouvrir = PendingIntent.getActivity(
            contexte, 0,
            Intent(contexte, ActivitePrincipale::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(contexte, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Du nouveau dans Service Locator")
            .setContentText("Ouvrez l'application pour voir ce qui a changé.")
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        contexte.getSystemService(NotificationManager::class.java).notify(IDENTIFIANT, notification)
    }

    /** L'application est ouverte : la notification a dit ce qu'elle avait à dire. */
    fun retirer(contexte: Context) = contexte.getSystemService(NotificationManager::class.java).cancel(IDENTIFIANT)
}
