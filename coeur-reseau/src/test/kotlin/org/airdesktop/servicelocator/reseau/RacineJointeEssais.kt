package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Quelle racine a répondu : la nommer — l'entrée la plus précise, jamais
 * l'alias qui les couvre toutes —, et suivre l'état de la connexion.
 */
class RacineJointeEssais {
    private val racines: List<Pair<String, List<String>>> = listOf(
        "asl-root.air-desktop.org" to listOf("[2001:41d0:20a:900::1dd4]:6630", "[2001:41d0:20a:900::1d32]:6630", "178.32.16.250:6630", "178.32.16.249:6630"),
        "nitrogen.air-desktop.org" to listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"),
        "argon.air-desktop.org" to listOf("[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630"),
    )

    // ── Nommer ────────────────────────────────────────────────────────────────

    @Test
    fun sousAutomatiqueCEstLaRacineQuiEstNommee() {
        assertEquals("nitrogen.air-desktop.org", RacineJointe.nommer("[2001:41d0:20a:900::1dd4]:6630", racines))
        assertEquals("argon.air-desktop.org", RacineJointe.nommer("178.32.16.249:6630", racines))
    }

    /** L'ordre de la liste ne change rien : c'est la précision qui décide. */
    @Test
    fun lOrdreDeLaListeNeComptePas() {
        assertEquals("argon.air-desktop.org", RacineJointe.nommer("[2001:41d0:20a:900::1d32]:6630", racines.reversed()))
    }

    /** Seul l'alias la contient : c'est lui qu'on nomme. */
    @Test
    fun fauteDeMieuxLAlias() {
        assertEquals("asl-root", RacineJointe.nommer("[2001:db8::1]:6630", listOf("asl-root" to listOf("[2001:db8::1]:6630"))))
    }

    /** Une adresse que rien ne contient ne se nomme pas : l'écran la dit brute. */
    @Test
    fun uneAdresseInconnueNeSeNommePasEtSAfficheBrute() {
        assertNull(RacineJointe.nommer("192.0.2.7:6630", racines))
        assertEquals("192.0.2.7:6630", RacineJointe("192.0.2.7:6630", null).affichee)
        assertEquals("argon.air-desktop.org", RacineJointe("178.32.16.249:6630", "argon.air-desktop.org").affichee)
    }

    // ── L'état ────────────────────────────────────────────────────────────────

    /** Connecté, puis la connexion se perd, puis une autre aboutit ailleurs : l'état suit, sans rien demander. */
    @Test
    fun lEtatSuitConnexionPerteEtReconnexion() {
        val suivi = SuiviDeLaRacine()
        assertNull("rien n'est tenu avant la première connexion", suivi.racine.value)

        suivi.jointe("[2001:41d0:20a:900::1dd4]:6630", racines)
        assertEquals(RacineJointe("[2001:41d0:20a:900::1dd4]:6630", "nitrogen.air-desktop.org"), suivi.racine.value)

        suivi.perdue()
        assertNull(suivi.racine.value)

        suivi.jointe("[2001:41d0:20a:900::1d32]:6630", racines)
        assertEquals("argon.air-desktop.org", suivi.racine.value?.nom)
    }

    /**
     * La bascule : l'annuaire quitté se dit perdu, et celui qui le remplace
     * porte son propre état — l'écran, qui lit celui de l'annuaire en
     * service, change avec lui.
     */
    @Test
    fun laBasculeChangeDEtatEtLAncienSeDitPerdu(): Unit = runBlocking {
        val nitrogen = RacineDAnnuaire("nitrogen.air-desktop.org:6630", "nitrogen.air-desktop.org")
        val argon = RacineDAnnuaire("argon.air-desktop.org:6630", "argon.air-desktop.org")
        val fabriques = mutableListOf<AnnuaireSimule>()
        val choix = ChoixDAnnuaire(listOf(nitrogen, argon), object : MemoireDuChoix {
            override var adresse: String? = null
        }) { AnnuaireSimule().also { fabriques += it } }

        val ancien = choix.annuaire
        assertNotNull(ancien.racineJointe.value)
        choix.choisir(argon)

        assertNull("l'annuaire quitté n'a plus de connexion", ancien.racineJointe.value)
        assertEquals(2, fabriques.size)
        assertNotNull("le nouveau porte son propre état", choix.annuaire.racineJointe.value)
    }
}
