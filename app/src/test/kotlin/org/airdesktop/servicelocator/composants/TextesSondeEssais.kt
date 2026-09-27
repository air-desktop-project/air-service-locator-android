package org.airdesktop.servicelocator.composants

import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** D'où vient la sonde d'un service fédéré (décision 60) — les mots de l'application iOS/macOS (PR iOS #32), à la lettre. */
class TextesSondeEssais {
    private val point = PointEcoute(PointEcoute.Protocole.TCP, 8080)
    private val speedy = Identifiant.analyser("n-7MSV5RPCXBZH25PQM4ZPE5X87P", Genre.ANNUAIRE)
    private val il_y_a_4_min = Instant.now().minusSeconds(240)
    private val joignable = Joignabilite.Joignable(il_y_a_4_min, "[2001:db8::1]:8080")

    private fun service(verdict: Joignabilite, sondeLocale: Boolean, sondePar: Identifiant? = speedy) = Service(
        Identifiant(Genre.SERVICE, ByteArray(16) { 9 }), "essai", listOf(point), Service.Etat.Annonce(Instant.EPOCH),
        mapOf(point to verdict), sondePar = sondePar, sondeLocale = sondeLocale,
    )

    @Test
    fun lEtatDUneSondeDeLInterieur() {
        assertEquals("Joignable depuis la machine", libelleDuVerdict(joignable, service(joignable, true)))
        assertEquals("Joignable depuis la machine", service(joignable, true).libelleEtat)
        assertEquals("Joignable", libelleDuVerdict(joignable, service(joignable, false)))
        val injoignable = Joignabilite.Injoignable(il_y_a_4_min)
        assertEquals("Annoncé, injoignable", libelleDuVerdict(injoignable, service(injoignable, true)))
    }

    /** « rapporté par l'annuaire <abrégé>, <date> » remplace « depuis l'annuaire, <date> », pour joignable et injoignable. */
    @Test
    fun leDetailDitQuiASonde() {
        assertEquals("rapporté par l'annuaire n-7MSV…X87P, il y a 4 min", detailDuVerdict(joignable, service(joignable, true)))
        assertEquals("rapporté par l'annuaire n-7MSV…X87P, il y a 4 min", service(joignable, true).detailEtat)
        val injoignable = Joignabilite.Injoignable(il_y_a_4_min)
        assertEquals("rapporté par l'annuaire n-7MSV…X87P, il y a 4 min", detailDuVerdict(injoignable, service(injoignable, false)))
        // « en cours » et « UDP — non sondé » inchangés ; sans sonde_par, comme avant.
        assertEquals("sonde en cours", detailDuVerdict(Joignabilite.EnCours, service(Joignabilite.EnCours, false)))
        assertEquals("UDP — non sondé", detailDuVerdict(Joignabilite.NonSonde, service(Joignabilite.NonSonde, false)))
        assertEquals("depuis l'annuaire, il y a 4 min", detailDuVerdict(joignable, service(joignable, false, sondePar = null)))
    }

    @Test
    fun laMiseEnGardeSeulementPourUnJoignableDeLInterieur() {
        assertEquals("Sondé depuis la machine elle-même : pas vérifié de l'extérieur.", miseEnGardeDuVerdict(joignable, service(joignable, true)))
        assertNull(miseEnGardeDuVerdict(joignable, service(joignable, false)))
        val injoignable = Joignabilite.Injoignable(il_y_a_4_min)
        assertNull(miseEnGardeDuVerdict(injoignable, service(injoignable, true)))
        assertNull(miseEnGardeDuVerdict(null, service(joignable, true)))
    }
}
