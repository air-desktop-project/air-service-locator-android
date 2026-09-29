package org.airdesktop.servicelocator.composants

import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Service
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Un service vivant lu sans adresse (0.40.0, décision 104) : il se dit « annoncé », et rien de plus. */
class TextesSansAdresseEssais {
    private val sansAdresse = Service(
        Identifiant(Genre.SERVICE, ByteArray(16) { 9 }), "depot", emptyList(), Service.Etat.Annonce(Instant.EPOCH), sansAdresse = true,
    )

    @Test
    fun unServiceSansAdresseSeDitAnnonceSansAdresse() {
        assertEquals("Annoncé", sansAdresse.libelleEtat)
        assertEquals("sans adresse", sansAdresse.detailEtat)
        assertEquals(Couleurs.accent, sansAdresse.couleur)
    }

    /** Sans adresse, le vivant reste vivant : il ne se confond pas avec un parti. */
    @Test
    fun unPartiResteUnParti() {
        val parti = sansAdresse.copy(etat = Service.Etat.Parti(null, null), sansAdresse = false)
        assertEquals("Parti", parti.libelleEtat)
    }

    @Test
    fun lExplicationDitQuiRecoitLesAdresses() {
        assertEquals(
            "Ce domaine vous donne de voir ce service, pas de le joindre : l'annuaire n'en rend les points d'écoute et les adresses qu'à qui y tient « localiser ».",
            TextesSansAdresse.explication,
        )
    }
}
