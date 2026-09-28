package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les racines désignées par leur identité (décisions 53–58, C20) : les deux
 * écritures d'`annuaire.json`, les locateurs qu'on écarte sans les résoudre,
 * l'entrée sans identité qu'on saute, la préférence, et le nom dit d'après l'identité.
 */
class AnnuaireIdentifieEssais {
    private val nitrogenN = "n-0PWT8HZD80QMSPPDZ5CQXXYHQC"
    private val argonN = "n-3K3P6H252W8K9370QG1YYTWBWB"
    private val nitrogenLoc = listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630")
    private val argonLoc = listOf("[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630")

    /** La forme exacte que l'application iOS lit (PR -ios #29). */
    private val fichier = """
        {"annuaires": [
          {"libelle": "Automatique", "adresse": "asl-root.air-desktop.org:6630", "nom": "asl-root.air-desktop.org",
           "racines": [
             {"annuaire": "$nitrogenN", "locateurs": ["[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"]},
             {"annuaire": "$argonN", "locateurs": ["[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630"]}]},
          {"adresse": "nitrogen.air-desktop.org:6630", "nom": "nitrogen.air-desktop.org",
           "annuaire": "$nitrogenN", "locateurs": ["[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"]},
          {"adresse": "argon.air-desktop.org:6630", "nom": "argon.air-desktop.org",
           "annuaire": "$argonN", "locateurs": ["[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630"]}
        ]}
    """.trimIndent()

    private class Memoire(override var adresse: String? = null) : MemoireDuChoix

    // ── Les deux écritures ────────────────────────────────────────────────────

    @Test
    fun lEcritureLongueEtLaCourteSeLisentAvecLeursIdentites() {
        val lue = ListeDAnnuaires.lire(fichier)
        assertEquals(3, lue.size)
        assertEquals("Automatique", lue[0].affichee)
        assertEquals(
            listOf(RacineIdentifiee(nitrogenN, nitrogenLoc), RacineIdentifiee(argonN, argonLoc)),
            lue[0].identites,
        )
        assertEquals(listOf(RacineIdentifiee(nitrogenN, nitrogenLoc)), lue[1].identites)
        assertEquals("nitrogen.air-desktop.org:6630", lue[1].adresse)
        assertTrue(lue.all { it.parIdentite })
    }

    @Test
    fun lEcritureCourteVientEnTeteQuandLesDeuxSeCumulent() {
        val lue = ListeDAnnuaires.lire(
            """{"annuaire": "$argonN", "locateurs": ["178.32.16.249:6630"],
                "racines": [{"annuaire": "$nitrogenN", "locateurs": ["178.32.16.250:6630"]}]}""",
        )
        assertEquals(listOf(argonN, nitrogenN), lue.single().identites.map { it.annuaire })
    }

    // ── Ce qu'on écarte, sans rien résoudre ───────────────────────────────────

    @Test
    fun unLocateurQuiNEstPasLitteralEstLaisseDeCote() {
        val lue = ListeDAnnuaires.lire(
            """{"annuaire": "$nitrogenN", "locateurs": ["nitrogen.air-desktop.org:6630", "[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250"]}""",
        )
        assertEquals(listOf("[2001:41d0:20a:900::1dd4]:6630"), lue.single().identites.single().locateurs)
    }

    @Test
    fun unIdentifiantDeTraversOuSansLocateurLitteralEcarteLaRacine() {
        assertEquals(
            emptyList<RacineDAnnuaire>(),
            ListeDAnnuaires.lire("""{"annuaire": "u-0PWT8HZD80QMSPPDZ5CQXXYHQC", "locateurs": ["178.32.16.250:6630"]}"""),
        )
        assertEquals(
            emptyList<RacineDAnnuaire>(),
            ListeDAnnuaires.lire("""{"annuaire": "$nitrogenN", "locateurs": ["nitrogen.air-desktop.org:6630"]}"""),
        )
    }

    @Test
    fun lesLocateursLitterauxSeJugentSansResolveur() {
        assertTrue(ListeDAnnuaires.estLitteral("[2001:41d0:20a:900::1dd4]:6630"))
        assertTrue(ListeDAnnuaires.estLitteral("178.32.16.250:6630"))
        assertFalse(ListeDAnnuaires.estLitteral("nitrogen.air-desktop.org:6630"))
        assertFalse(ListeDAnnuaires.estLitteral("[nitrogen]:6630"))
        assertFalse(ListeDAnnuaires.estLitteral("2001:41d0::1:6630"))
        assertFalse(ListeDAnnuaires.estLitteral("256.1.1.1:6630"))
        assertFalse(ListeDAnnuaires.estLitteral("178.32.16.250:0"))
        assertFalse(ListeDAnnuaires.estLitteral("178.32.16.250:70000"))
        assertFalse(ListeDAnnuaires.estLitteral("[]:6630"))
    }

    // ── Sans identité, rien à joindre ─────────────────────────────────────────

    @Test
    fun uneEntreeSansIdentiteEstLaisseeDeCoteEtLeJournalLeDit() {
        val melange = """
            {"annuaires": [
              {"adresse": "vieille.example:6630", "nom": "vieille.example"},
              {"annuaire": "$argonN", "locateurs": ["178.32.16.249:6630"]}
            ]}
        """.trimIndent()
        val journal = mutableListOf<String>()
        val lue = ListeDAnnuaires.lire(melange) { journal += it }
        assertEquals(listOf(argonN), lue.single().identites.map { it.annuaire })
        assertTrue(journal.single(), journal.single().contains("vieille.example:6630"))
        // Une entrée sans adresse prend pour nom celui que l'identité donne.
        assertEquals("argon.air-desktop.org", lue.single().nom)
        assertEquals("", lue.single().adresse)
    }

    @Test
    fun uneListeSansAucuneIdentiteNeDonneRien() {
        val journal = mutableListOf<String>()
        val lue = ListeDAnnuaires.lire("""{"adresse": "nitrogen.air-desktop.org:6630", "nom": "nitrogen.air-desktop.org"}""") { journal += it }
        assertEquals(emptyList<RacineDAnnuaire>(), lue)
        assertEquals(1, journal.size)
    }

    // ── La préférence ─────────────────────────────────────────────────────────

    @Test
    fun laPreferenceRetientLAdresseSinonLesIdentites() = runBlocking {
        val lue = ListeDAnnuaires.lire(fichier)
        assertEquals("nitrogen.air-desktop.org:6630", lue[1].cle)
        val sansAdresse = ListeDAnnuaires.lire(
            """{"racines": [{"annuaire": "$nitrogenN", "locateurs": ["178.32.16.250:6630"]}, {"annuaire": "$argonN", "locateurs": ["178.32.16.249:6630"]}]}""",
        ).single()
        assertEquals("$nitrogenN,$argonN", sansAdresse.cle)

        // Un choix retenu hier (par l'adresse) l'est encore.
        val memoire = Memoire("argon.air-desktop.org:6630")
        val choix = ChoixDAnnuaire(lue, memoire) { AnnuaireSimule() }
        assertEquals(argonN, choix.choisie.identites.single().annuaire)
        assertTrue(choix.choisir(lue[0]))
        assertEquals("asl-root.air-desktop.org:6630", memoire.adresse)

        // Une entrée sans adresse se retient par ses identités.
        val seule = Memoire()
        val choixSansAdresse = ChoixDAnnuaire(listOf(lue[1], sansAdresse), seule) { AnnuaireSimule() }
        assertTrue(choixSansAdresse.choisir(sansAdresse))
        assertEquals("$nitrogenN,$argonN", seule.adresse)
    }

    // ── Le nom, d'après l'identité ────────────────────────────────────────────

    @Test
    fun laRacineJointeSeNommeParSonIdentite() {
        val lue = ListeDAnnuaires.lire(fichier)
        assertEquals("argon.air-desktop.org", RacineJointe.nommerParIdentite("[2001:41d0:20a:900::1d32]:6630", lue))
        assertEquals("nitrogen.air-desktop.org", RacineJointe.nommerParIdentite("178.32.16.250:6630", lue))
        assertNull(RacineJointe.nommerParIdentite("[2001:db8::1]:6630", lue))
    }

    @Test
    fun uneIdentiteInconnueSeDitAbregee() {
        val inconnue = "n-7MSV5RPCXBZH25PQM4ZPE5X87P"
        val lue = ListeDAnnuaires.lire("""{"annuaire": "$inconnue", "locateurs": ["[2001:db8::7]:6630"]}""")
        assertEquals("n-7MSV…X87P", RacineJointe.nommerParIdentite("[2001:db8::7]:6630", lue))
        assertEquals("n-7MSV…X87P", lue.single().nom)
    }

    @Test
    fun leJournalDitLeLocateurEtLIdentiteAttendue() {
        val sansAdresse = RacineDAnnuaire("", "argon.air-desktop.org", identites = listOf(RacineIdentifiee(argonN, argonLoc)))
        assertEquals("[2001:41d0:20a:900::1d32]:6630=$argonN", sansAdresse.affichePourLeJournal)
        // L'adresse n'est qu'une clé de préférence : le journal dit toujours ce qui est joint.
        assertEquals("[2001:41d0:20a:900::1d32]:6630=$argonN", sansAdresse.copy(adresse = "argon.air-desktop.org:6630").affichePourLeJournal)
    }
}
