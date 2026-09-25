package org.airdesktop.servicelocator.notifications

import android.content.Context
import android.util.Log
import org.airdesktop.servicelocator.ApplicationServiceLocator
import org.unifiedpush.android.connector.MessagingReceiver

/**
 * Ce que le distributeur UnifiedPush dit à l'application.
 *
 * **Exporté**, parce que c'est une autre application — le distributeur — qui
 * l'appelle. Le connecteur ne transmet un appel que s'il porte le jeton
 * d'inscription qu'il a lui-même tiré : une application quelconque ne peut
 * pas poser de faux point. Et le contenu d'un message n'est jamais lu, de
 * sorte qu'un distributeur ne peut rien faire dire à l'application.
 */
class RecepteurPoussee : MessagingReceiver() {
    private fun Context.application() = applicationContext as ApplicationServiceLocator

    /**
     * Un point neuf — à l'inscription, ou quand le distributeur en change. Il
     * est retenu tout de suite ; il ne se dépose que si quelqu'un est devant
     * l'application : déposer, c'est se connecter, et se connecter demande
     * une empreinte. Sinon, il part à la prochaine ouverture.
     */
    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        val application = context.application()
        application.notifications.point = endpoint
        if (application.activiteAuPremierPlan != null) application.session.deposerPoint()
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.i("poussee", "le distributeur a refusé l'inscription")
        context.application().notifications.oublierPoint()
    }

    /**
     * L'inscription est retirée — par l'utilisateur, dans le distributeur ou
     * ici. **Rien ne part vers l'annuaire** : il n'a pas de verbe de retrait,
     * et apprendra au premier envoi que le point est mort (`protocole.md` §2.2).
     */
    override fun onUnregistered(context: Context, instance: String) {
        context.application().notifications.oublierPoint()
    }

    /**
     * **Le message est vide, et il n'est pas lu.** Qu'il porte quelque chose
     * ou non, ce qui s'affiche est la même phrase générique ; ce qui a changé,
     * l'application le relit à l'ouverture, sur sa propre connexion.
     */
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        context.application().notifications.aRelire = true
        Nouvelles.montrer(context)
    }
}
