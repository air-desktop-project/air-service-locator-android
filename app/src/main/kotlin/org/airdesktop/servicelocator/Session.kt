package org.airdesktop.servicelocator

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.identite.CleAppareil
import org.airdesktop.servicelocator.identite.deCetAppareil
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Signataire
import org.airdesktop.servicelocator.reseau.Annuaire

/**
 * Ce que tous les écrans partagent : l'annuaire à qui parler, et le compte de
 * cet appareil.
 */
class Session(
    val annuaire: Annuaire,
    val identite: IdentiteLocale,
    /** D'où vient la clé : le Keystore sur un appareil, une clé logicielle dans un essai. */
    private val signataire: (FragmentActivity) -> Signataire = { CleAppareil.ouOuvrir().avec(it) },
    /** Comment on ouvre un compte — séparé de l'annuaire parce qu'en démonstration, l'ouverture peuple aussi l'annuaire. */
    private val ouverture: suspend (Signataire) -> Compte,
) {
    var compte: Compte? by mutableStateOf(null)
        private set

    /** Relit le compte que l'annuaire connaît pour cet appareil. */
    suspend fun rafraichirCompte() {
        compte = runCatching { annuaire.compte() }.getOrNull()
        if (compte != null) seDecrire()
    }

    /**
     * Dit à l'annuaire ce que cet appareil est — plate-forme et modèle, jamais
     * le nom que l'utilisateur lui a donné (`docs/modele.md` §2.2). Juste après
     * chaque preuve : c'est le moment où l'appareil parle de lui sur sa propre
     * connexion.
     *
     * **Une étiquette qui n'a pas pu se poser n'est pas une panne.** Un
     * annuaire qui ne sert pas encore ce verbe rend `404` ; le compte, lui, est
     * là. On ne le dit pas à l'écran, et l'annuaire réel n'en garde pas trace
     * comme posée — elle repartira à la prochaine preuve.
     */
    private suspend fun seDecrire() {
        runCatching { annuaire.decrire(Appareil.Description.deCetAppareil()) }
            .onFailure { Log.i("Session", "la description de cet appareil n'a pas été posée : ${it.message}") }
    }

    /**
     * Ouvre le compte : la clé de l'appareil prouve qu'elle est détenue, sur le
     * défi de l'annuaire et la liaison du canal.
     *
     * **C'est ici que la biométrie est demandée**, par le Keystore, au moment
     * de signer — et nulle part avant. Sans confirmation, la clé ne signe pas,
     * et rien ne part.
     */
    suspend fun ouvrirCompte(activite: FragmentActivity) {
        compte = ouverture(signataire(activite))
        seDecrire()
    }

    /**
     * La clé publique de cet appareil — ce que le nouveau téléphone montre à
     * l'ancien. La lire ne demande aucun geste : seule la signature en
     * demande un.
     */
    fun clePublique(activite: FragmentActivity): ByteArray = signataire(activite).clePublique

    /** Rejoint un compte, depuis ce téléphone-ci, avec l'invitation que l'autre a rendue. Le geste est demandé au moment de prouver la clé. */
    suspend fun rejoindre(activite: FragmentActivity, compte: Identifiant, appareil: Identifiant) {
        this.compte = annuaire.rejoindre(compte, appareil, signataire(activite))
        seDecrire()
    }

    suspend fun definirAlias(alias: String?) {
        annuaire.definirAlias(alias)
        rafraichirCompte()
    }
}

val LocalSession = compositionLocalOf<Session> { error("aucune session") }
