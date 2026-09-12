package org.airdesktop.servicelocator

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.identite.CleAppareil
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.modele.Compte
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
    }

    suspend fun definirAlias(alias: String?) {
        annuaire.definirAlias(alias)
        rafraichirCompte()
    }
}

val LocalSession = compositionLocalOf<Session> { error("aucune session") }
