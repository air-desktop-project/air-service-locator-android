package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le choix de la racine : la liste qu'on lit, ce qu'on retient, et la bascule. */
class ChoixDAnnuaireEssais {
    private val nitrogen = RacineDAnnuaire("nitrogen.air-desktop.org:6630", "nitrogen.air-desktop.org")
    private val argon = RacineDAnnuaire("argon.air-desktop.org:6630", "argon.air-desktop.org")
    private val automatique = RacineDAnnuaire("asl-root.air-desktop.org:6630", "asl-root.air-desktop.org", "Automatique")

    private val nitrogenN = RacineIdentifiee("n-0PWT8HZD80QMSPPDZ5CQXXYHQC", listOf("178.32.16.250:6630"))
    private val argonN = RacineIdentifiee("n-3K3P6H252W8K9370QG1YYTWBWB", listOf("178.32.16.249:6630"))

    private val liste = """
        {"annuaires": [
          {"adresse": "nitrogen.air-desktop.org:6630", "nom": "nitrogen.air-desktop.org",
           "annuaire": "${nitrogenN.annuaire}", "locateurs": ["178.32.16.250:6630"]},
          {"adresse": "argon.air-desktop.org:6630", "nom": "argon.air-desktop.org",
           "annuaire": "${argonN.annuaire}", "locateurs": ["178.32.16.249:6630"]},
          {"adresse": "asl-root.air-desktop.org:6630", "nom": "asl-root.air-desktop.org", "libelle": "Automatique",
           "racines": [{"annuaire": "${nitrogenN.annuaire}", "locateurs": ["178.32.16.250:6630"]},
                       {"annuaire": "${argonN.annuaire}", "locateurs": ["178.32.16.249:6630"]}]}
        ]}
    """.trimIndent()

    private class Memoire(override var adresse: String? = null) : MemoireDuChoix

    // ── La liste ──────────────────────────────────────────────────────────────

    @Test
    fun laListeSeLitDansSonOrdreAvecSesLibelles() {
        val lue = ListeDAnnuaires.lire(liste)
        assertEquals(
            listOf(
                nitrogen.copy(identites = listOf(nitrogenN)),
                argon.copy(identites = listOf(argonN)),
                automatique.copy(identites = listOf(nitrogenN, argonN)),
            ),
            lue,
        )
        assertEquals("argon.air-desktop.org", lue[1].affichee)
        assertEquals("Automatique", lue[2].affichee)
    }

    @Test
    fun uneEntreeSeuleEstUneListeDUnElement() {
        val lue = ListeDAnnuaires.lire("""{"adresse": "192.0.2.1:6630", "nom": "annuaire", "annuaire": "${argonN.annuaire}", "locateurs": ["192.0.2.1:6630"]}""")
        assertEquals(listOf(RacineDAnnuaire("192.0.2.1:6630", "annuaire", identites = listOf(RacineIdentifiee(argonN.annuaire, listOf("192.0.2.1:6630"))))), lue)
    }

    @Test
    fun unTexteIllisibleRendUneListeVideEtUneEntreeSansIdentiteEstSautee() {
        assertEquals(emptyList<RacineDAnnuaire>(), ListeDAnnuaires.lire("pas du json"))
        assertEquals(emptyList<RacineDAnnuaire>(), ListeDAnnuaires.lire("""{"annuaires": "nitrogen"}"""))
        val lue = ListeDAnnuaires.lire(
            """{"annuaires": [{"adresse": "a:1", "nom": "a"}, {"adresse": "b:1", "nom": "b", "annuaire": "${argonN.annuaire}", "locateurs": ["178.32.16.249:6630"]}]}""",
        )
        assertEquals(listOf(RacineDAnnuaire("b:1", "b", identites = listOf(argonN))), lue)
    }

    @Test
    fun deuxEntreesALaMemeAdresseNEnFontQuUne() {
        val lue = ListeDAnnuaires.lire(
            """{"annuaires": [
                {"adresse": "a:1", "nom": "premier", "annuaire": "${nitrogenN.annuaire}", "locateurs": ["178.32.16.250:6630"]},
                {"adresse": "a:1", "nom": "second", "annuaire": "${argonN.annuaire}", "locateurs": ["178.32.16.249:6630"]}]}""",
        )
        assertEquals(listOf(RacineDAnnuaire("a:1", "premier", identites = listOf(nitrogenN))), lue)
    }

    // ── Le choix retenu ───────────────────────────────────────────────────────

    @Test
    fun parDefautLaPremiere(): Unit = runBlocking {
        val choix = ChoixDAnnuaire(listOf(nitrogen, argon), Memoire()) { AnnuaireSimule() }
        assertEquals(nitrogen, choix.choisie)
        assertTrue(choix.offreUnChoix)
        assertFalse(ChoixDAnnuaire(listOf(nitrogen), Memoire()) { AnnuaireSimule() }.offreUnChoix)
    }

    @Test
    fun leChoixRetenuEstRepris(): Unit = runBlocking {
        val memoire = Memoire()
        ChoixDAnnuaire(listOf(nitrogen, argon), memoire) { AnnuaireSimule() }.choisir(argon)
        assertEquals(argon.adresse, memoire.adresse)
        // Au lancement suivant, la même mémoire désigne argon.
        assertEquals(argon, ChoixDAnnuaire(listOf(nitrogen, argon), memoire) { AnnuaireSimule() }.choisie)
    }

    @Test
    fun uneRacineRetireeDeLaListeRetombeSurLaPremiere() {
        val choix = ChoixDAnnuaire(listOf(nitrogen, automatique), Memoire(argon.adresse)) { AnnuaireSimule() }
        assertEquals(nitrogen, choix.choisie)
    }

    // ── La bascule ────────────────────────────────────────────────────────────

    @Test
    fun basculerFermeLAncienUneFoisEtGardeLeCompte(): Unit = runBlocking {
        val fabriques = mutableListOf<Pair<RacineDAnnuaire, AnnuaireSimule>>()
        val choix = ChoixDAnnuaire(listOf(nitrogen, argon), Memoire()) { racine ->
            AnnuaireSimule().also { fabriques += racine to it }
        }
        val ancien = choix.annuaire
        val compte = ancien.ouvrirCompte(CleLogicielle())

        assertTrue(choix.choisir(argon))

        assertEquals(1, ancien.fermetures)
        assertEquals(listOf(nitrogen, argon), fabriques.map { it.first })
        assertSame(fabriques.last().second, choix.annuaire)
        assertEquals(0, choix.annuaire.fermetures)
        assertEquals(argon, choix.choisie)
        // Fermer n'efface rien : l'ancien banc sait toujours le compte qu'il a ouvert.
        assertEquals(compte, ancien.compte())
    }

    @Test
    fun rechoisirLaMemeNeFermeRien(): Unit = runBlocking {
        val memoire = Memoire()
        val choix = ChoixDAnnuaire(listOf(nitrogen, argon), memoire) { AnnuaireSimule() }
        val annuaire = choix.annuaire
        assertFalse(choix.choisir(nitrogen))
        assertEquals(0, annuaire.fermetures)
        assertSame(annuaire, choix.annuaire)
        assertNull(memoire.adresse)
    }
}
