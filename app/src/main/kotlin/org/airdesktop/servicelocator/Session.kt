package org.airdesktop.servicelocator

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.identite.confirmer
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.reseau.Annuaire
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire

/**
 * Ce que tous les écrans partagent : l'annuaire à qui parler, et le compte de
 * cet appareil.
 */
class Session(
    val annuaire: Annuaire,
    val identite: IdentiteLocale,
    /** Comment on ouvre un compte — séparé de l'annuaire parce qu'en démonstration, l'ouverture peuple aussi l'annuaire. */
    private val ouverture: suspend () -> Compte,
) {
    var compte: Compte? by mutableStateOf(null)
        private set

    /** Relit le compte que l'annuaire connaît pour cet appareil. */
    suspend fun rafraichirCompte() {
        compte = runCatching { annuaire.compte() }.getOrNull()
    }

    /** Ouvre le compte, après confirmation biométrique. Sans confirmation, rien ne part. */
    suspend fun ouvrirCompte(activite: FragmentActivity) {
        if (!identite.confirmer(activite, "Ouvrir votre compte", "Votre identité est confirmée sur cet appareil et n'en sort pas.")) {
            throw ErreurAnnuaire.NonConfirme
        }
        compte = ouverture()
    }

    suspend fun definirAlias(alias: String?) {
        annuaire.definirAlias(alias)
        rafraichirCompte()
    }
}

val LocalSession = compositionLocalOf<Session> { error("aucune session") }
