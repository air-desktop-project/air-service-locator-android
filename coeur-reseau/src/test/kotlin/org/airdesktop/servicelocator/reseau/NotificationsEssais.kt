package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Les notifications sans tiers, côté application (`protocole.md` §2.2) : le dépôt, la différence, l'état. */
class NotificationsEssais {
    private val instant = Instant.ofEpochSecond(1_700_000_000)
    private fun id(genre: Genre, octet: Int) = Identifiant(genre, ByteArray(16) { octet.toByte() })

    // ── Le dépôt ──────────────────────────────────────────────────────────────

    @Test
    fun lePointSeDeposePourSoiEtLeNeufRemplaceLAncien(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        // Sans compte, pas d'appareil à qui le donner.
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.deposerPoint("https://ntfy.example.org/up1") } }
        annuaire.ouvrirCompte(CleLogicielle())
        val moi = annuaire.appareils().single { it.estCeluiCi }.id
        annuaire.deposerPoint("https://ntfy.example.org/up1")
        assertEquals("https://ntfy.example.org/up1", annuaire.pointDe(moi))
        annuaire.deposerPoint("https://ntfy.example.org/up2")
        assertEquals("https://ntfy.example.org/up2", annuaire.pointDe(moi))
    }

    @Test
    fun unPointMalFormeEstRefuseAvecSaRegleEtNeRemplaceRien(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        annuaire.ouvrirCompte(CleLogicielle())
        val moi = annuaire.appareils().single { it.estCeluiCi }.id
        annuaire.deposerPoint("https://ntfy.example.org/up1")
        val refus = assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) {
            runBlocking { annuaire.deposerPoint("http://192.168.1.10:8080/up") }
        }
        assertTrue(refus.champ, refus.champ.contains("https://"))
        assertEquals("https://ntfy.example.org/up1", annuaire.pointDe(moi))
    }

    @Test
    fun lePointPartAvecLAppareilRevoque(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        annuaire.ouvrirCompte(CleLogicielle())
        val premier = annuaire.appareils().single { it.estCeluiCi }.id
        annuaire.deposerPoint("https://ntfy.example.org/premier")
        // Un second appareil rejoint, dépose le sien, et révoque le premier : le point du premier part avec lui.
        val autre = CleLogicielle()
        val enrole = annuaire.enrolerAppareil(autre.clePublique)
        annuaire.rejoindre(annuaire.compte()!!.identifiant, enrole.id, autre)
        annuaire.deposerPoint("https://ntfy.example.org/second")
        annuaire.revoquerAppareil(premier)
        assertNull(annuaire.pointDe(premier))
        assertEquals("https://ntfy.example.org/second", annuaire.pointDe(enrole.id))
    }

    // ── La différence ─────────────────────────────────────────────────────────

    private val moi = id(Genre.UTILISATEUR, 1)
    private val alice = id(Genre.UTILISATEUR, 2)
    private fun recue(octet: Int, revoquee: Boolean = false) =
        Autorisation(id(Genre.AUTORISATION, octet), alice, moi, Autorisation.Portee.Tout, "", instant, if (revoquee) instant else null)
    private fun donnee(octet: Int) = Autorisation(id(Genre.AUTORISATION, octet), moi, alice, Autorisation.Portee.Tout, "", instant)

    @Test
    fun uneAutorisationNeuveEstSignaleeEtRetenue() {
        val vieille = recue(10)
        val neuve = recue(11)
        val lecture = Nouveautes.lire(listOf(vieille, neuve, donnee(12)), moi, setOf(vieille.id))
        assertEquals(listOf(neuve), lecture.nouvelles)
        assertEquals(setOf(vieille.id, neuve.id), lecture.aRetenir)
    }

    @Test
    fun ceQuiADejaEteVuNestPasNeuf() {
        val a = recue(10)
        val b = recue(11)
        val lecture = Nouveautes.lire(listOf(a, b), moi, setOf(a.id, b.id))
        assertTrue(lecture.nouvelles.isEmpty())
    }

    @Test
    fun rienDeNeufSansAutorisationEtCeQueJaiDonneNeComptePas() {
        assertTrue(Nouveautes.lire(emptyList(), moi, emptySet()).nouvelles.isEmpty())
        assertTrue(Nouveautes.lire(listOf(donnee(12)), moi, emptySet()).nouvelles.isEmpty())
    }

    @Test
    fun uneRevoqueeAvantDAvoirEteVueNestPasAnnonceeMaisEstRetenue() {
        val retiree = recue(10, revoquee = true)
        val lecture = Nouveautes.lire(listOf(retiree), moi, emptySet())
        assertTrue(lecture.nouvelles.isEmpty())
        assertEquals(setOf(retiree.id), lecture.aRetenir)
    }

    @Test
    fun laPremiereLecturePoseLaReferenceSansRienSignaler() {
        val a = recue(10)
        val lecture = Nouveautes.lire(listOf(a), moi, null)
        assertTrue(lecture.nouvelles.isEmpty())
        assertEquals(setOf(a.id), lecture.aRetenir)
    }

    // ── L'état, et d'abord sans distributeur ──────────────────────────────────

    @Test
    fun sansDistributeurLEtatLeDitEtRienNeCasse() {
        assertEquals(EtatNotifications.SansDistributeur, EtatNotifications.de(emptyList(), null, null, null, null))
        // Le distributeur choisi a été désinstallé : c'est comme s'il n'y en avait pas, pas une erreur.
        assertEquals(EtatNotifications.SansDistributeur, EtatNotifications.de(emptyList(), "io.heckel.ntfy", "https://ntfy.sh/up", "https://ntfy.sh/up", null))
    }

    @Test
    fun desDistributeursSansChoixSeProposentSansQuOnEnImposeUn() {
        val installes = listOf("io.heckel.ntfy", "org.unifiedpush.distributor.nextpush")
        assertEquals(EtatNotifications.AChoisir(installes), EtatNotifications.de(installes, null, null, null, null))
    }

    @Test
    fun choisiLEtatSuitLePointEtSonDepot() {
        val ntfy = listOf("io.heckel.ntfy")
        assertEquals(EtatNotifications.EnAttente("io.heckel.ntfy"), EtatNotifications.de(ntfy, "io.heckel.ntfy", null, null, null))
        assertEquals(
            EtatNotifications.Actif("io.heckel.ntfy", depose = false, refus = null),
            EtatNotifications.de(ntfy, "io.heckel.ntfy", "https://ntfy.sh/up", null, null),
        )
        assertEquals(
            EtatNotifications.Actif("io.heckel.ntfy", depose = true, refus = null),
            EtatNotifications.de(ntfy, "io.heckel.ntfy", "https://ntfy.sh/up", "https://ntfy.sh/up", "vieux refus"),
        )
        // Un point neuf, pas encore déposé : l'ancien refus reste dit, jusqu'au prochain dépôt.
        assertEquals(
            EtatNotifications.Actif("io.heckel.ntfy", depose = false, refus = "https:// et rien d'autre"),
            EtatNotifications.de(ntfy, "io.heckel.ntfy", "http://ntfy.local/up", null, "https:// et rien d'autre"),
        )
    }
}
