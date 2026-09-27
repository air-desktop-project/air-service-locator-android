package org.airdesktop.servicelocator.composants

import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** D'où vient la sonde d'un service fédéré, dit à l'écran (décision 60). */
class TextesSondeEssais {
    private val point = PointEcoute(PointEcoute.Protocole.TCP, 8080)
    private val joignable = Joignabilite.Joignable(Instant.EPOCH, "[2001:db8::1]:8080")

    private fun service(sondeLocale: Boolean, sondePar: Identifiant? = null) = Service(
        Identifiant(Genre.SERVICE, ByteArray(16) { 9 }), "essai", listOf(point), Service.Etat.Annonce(Instant.EPOCH),
        mapOf(point to joignable), sondePar = sondePar, sondeLocale = sondeLocale,
    )

    @Test
    fun uneSondeDeLInterieurNeDitPasJoignable() {
        assertEquals("Joignable depuis la machine elle-même — pas vérifié de l'extérieur", libelleDuVerdict(joignable, service(true)))
        assertEquals("Joignable depuis la machine elle-même — pas vérifié de l'extérieur", service(true).libelleEtat)
        assertEquals("Joignable", libelleDuVerdict(joignable, service(false)))
        // Un autre verdict que « joignable » ne change pas.
        assertEquals("Annoncé, injoignable", libelleDuVerdict(Joignabilite.Injoignable(Instant.EPOCH), service(true)))
    }

    @Test
    fun rapporteParNommeOuAbrege() {
        assertEquals("rapporté par nitrogen.air-desktop.org", TextesSonde.rapportePar(Identifiant.analyser("n-0PWT8HZD80QMSPPDZ5CQXXYHQC", Genre.ANNUAIRE)))
        val inconnu = Identifiant.analyser("n-7MSV5RPCXBZH25PQM4ZPE5X87P", Genre.ANNUAIRE)
        assertEquals("rapporté par ${inconnu.abrege}", TextesSonde.rapportePar(inconnu))
    }
}
