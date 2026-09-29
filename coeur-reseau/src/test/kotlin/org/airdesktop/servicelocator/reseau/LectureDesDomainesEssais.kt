package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.EtatDeLaPaire
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.VoieDuMembre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Ce que le serveur écrit (`asl-api` : `domaine.rs`, `annuaire.rs`), lu. */
class LectureDesDomainesEssais {
    private val d = Identifiant(Genre.DOMAINE, ByteArray(16) { 1 }).texte
    private val u = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 2 }).texte
    private val n = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 3 }).texte
    private val n2 = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 4 }).texte
    private val m = Identifiant(Genre.MACHINE, ByteArray(16) { 5 }).texte

    @Test
    fun laListeDesDomaines() {
        val corps = """[{"domaine":"$d","proprietaire":"$u","alias":"Maison","heberge_par":"racines","droits":["administrer","rattacher","voir","localiser"]},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"$n","droits":["voir"]}]"""
        val lus = LectureDesDomaines.domaines(corps)
        assertEquals(2, lus.size)
        assertEquals("Maison", lus[0].alias)
        assertNull(lus[0].hebergePar)
        assertEquals(4, lus[0].droits.size)
        assertNull(lus[1].alias)
        assertEquals(n, lus[1].hebergePar?.texte)
    }

    @Test
    fun uneEntreeIllisibleEstSauteeEtUnHebergeurInconnuSeLitCommeLesRacines() {
        val corps = """[{"domaine":"pas-un-id","proprietaire":"$u"},{"domaine":"$d","proprietaire":"$u","heberge_par":"ailleurs","droits":[],"nouveau":1}]"""
        val lus = LectureDesDomaines.domaines(corps)
        assertEquals(1, lus.size)
        assertNull(lus[0].hebergePar)
    }

    @Test
    fun laSorteDitLeDomaineRacineEtRienDAutre() {
        val quatre = """"droits":["administrer","rattacher","voir","localiser"]"""
        val corps = """[{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre,"sorte":"racine"},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre,"sorte":"branche"},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre,"sorte":true},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre,"sorte":{"nom":"racine"}},""" +
            """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines",$quatre,"sorte":null}]"""
        val lus = LectureDesDomaines.domaines(corps)
        // Aucune forme ne fait sauter l'entrée.
        assertEquals(6, lus.size)
        // Présente : R.
        assertTrue(lus[0].racine)
        assertFalse(lus[0].seConfie)
        // Absente, un mot inconnu, une autre forme qu'une chaîne, null : un domaine ordinaire.
        for (i in 1..5) {
            assertFalse(lus[i].racine)
            assertTrue(lus[i].seConfie)
        }
        // Le détail la lit aussi.
        val detail = LectureDesDomaines.detail("""{"domaine":"$d","proprietaire":"$u",$quatre,"sorte":"racine","machines":[]}""")!!
        assertTrue(detail.domaine.racine)
        assertFalse(LectureDesDomaines.detail("""{"domaine":"$d","proprietaire":"$u",$quatre,"machines":[]}""")!!.domaine.racine)
    }

    @Test
    fun leDetailEtSesMachines() {
        val corps = """{"domaine":"$d","proprietaire":"$u","heberge_par":"racines","droits":["voir"],"groupes":[],""" +
            """"machines":[{"machine":"$m","proprietaire":"$u","nom":"grenier"},{"machine":"$m","proprietaire":"$u"}]}"""
        val detail = LectureDesDomaines.detail(corps)!!
        assertEquals(2, detail.machines.size)
        assertEquals("grenier", detail.machines[0].affichee)
        assertEquals(m, detail.machines[1].affichee)
    }

    @Test
    fun leDomaineCreeEtLeCode() {
        assertEquals(d, LectureDesDomaines.domaineCree("""{"domaine":"$d"}""")?.texte)
        val code = LectureDesDomaines.code("""{"code":"4K9M2-P7R1T","expire_a":1790000000000}""")!!
        assertEquals("4K9M2-P7R1T", code.code)
        assertEquals(Instant.ofEpochMilli(1790000000000), code.expireA)
        assertNull(LectureDesDomaines.code("""{"code":"4K9M2-P7R1T"}"""))
    }

    @Test
    fun lesInscriptionsTellesQueLAnnuaireLesEcrit() {
        val corps = """[{"annuaire":"$n","etat":"attendue","adresse":"speedy:6630","expire_a":1790000000000},""" +
            """{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"speedy:6630"},""" +
            """{"membre":"$n2","annuaire":"$n","proprietaire":"$u","etat":"en attente","adresse":"[2a01::1]:6630"},""" +
            """{"membre":"$n2","annuaire":"$n","etat":"suspendue","adresse":"helium:6630"}]"""
        val lues = LectureDesDomaines.inscriptions(corps)
        assertEquals(4, lues.size)
        assertEquals(EtatDInscription.Attendue, lues[0].etat)
        assertNull(lues[0].membre)
        assertTrue(lues[1].estTitulaire)
        assertEquals(EtatDInscription.EnAttente, lues[2].etat)
        assertEquals(u, lues[2].proprietaire?.texte)
        assertEquals(EtatDInscription.Inconnu, lues[3].etat)
        assertEquals("suspendue", lues[3].motDeLEtat)
    }

    @Test
    fun laPaireEtLaVoieDeChaqueMembre() {
        // La forme d'`annuaires.md` §2 quinquies : le titulaire tient, son second s'est tu.
        val corps = """[{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"speedy.example:6630","locateurs":["[2a01::1]:6630"],""" +
            """"paire":"reglee","voie":"ouverte"},""" +
            """{"membre":"$n2","annuaire":"$n","etat":"acceptée","adresse":"helium.example:6630","paire":"sans-peer","voie":"tombee"}]"""
        val lues = LectureDesDomaines.inscriptions(corps)
        assertEquals(EtatDeLaPaire.Reglee, lues[0].paire)
        assertEquals(VoieDuMembre.Ouverte, lues[0].voie)
        assertEquals(EtatDeLaPaire.SansPeer, lues[1].paire)
        assertEquals(VoieDuMembre.Tombee, lues[1].voie)
        assertEquals(EtatDeLaPaire.Seul, LectureDesDomaines.inscriptions("""[{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":"seul"}]""").single().paire)
        assertEquals(EtatDeLaPaire.PeerInconnu, LectureDesDomaines.inscriptions("""[{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":"peer-inconnu"}]""").single().paire)
    }

    @Test
    fun uneVoieOuUnePaireAbsenteInconnueOuMalFormeeNeCassePasLaLecture() {
        val corps = """[{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1"},""" +
            """{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":"jumelee","voie":"entrouverte"},""" +
            """{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":null,"voie":null},""" +
            """{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":true,"voie":1},""" +
            """{"membre":"$n","annuaire":"$n","etat":"acceptée","adresse":"a:1","paire":"","voie":""}]"""
        val lues = LectureDesDomaines.inscriptions(corps)
        assertEquals(5, lues.size)
        // Absent : on ne sait pas.
        assertNull(lues[0].paire)
        assertNull(lues[0].voie)
        // Un mot d'un annuaire plus récent : lu, mais sans rien en conclure.
        assertEquals(EtatDeLaPaire.Inconnu, lues[1].paire)
        assertEquals(VoieDuMembre.Inconnue, lues[1].voie)
        // null, une autre forme qu'une chaîne, une chaîne vide : comme absents.
        for (i in 2..4) {
            assertNull(lues[i].paire)
            assertNull(lues[i].voie)
        }
    }
}
