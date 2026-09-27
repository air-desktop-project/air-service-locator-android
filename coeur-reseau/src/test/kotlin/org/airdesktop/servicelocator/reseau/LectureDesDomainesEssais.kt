package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.junit.Assert.assertEquals
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
}
