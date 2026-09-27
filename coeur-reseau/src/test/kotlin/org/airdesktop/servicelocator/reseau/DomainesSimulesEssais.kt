package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Les règles des domaines et des annuaires locaux (`protocole.md` §2.2), tenues par le banc. */
class DomainesSimulesEssais {
    private val instant = Instant.parse("2026-09-27T10:00:00Z")

    private fun avecCompte(bloc: suspend (AnnuaireSimule) -> Unit) = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        annuaire.ouvrirCompte(CleLogicielle())
        bloc(annuaire)
    }

    @Test
    fun unPremierDomaineNaitAvecLeCompteEtLeDernierNeSeSupprimePas() = avecCompte { annuaire ->
        val premier = annuaire.domaines().single()
        assertThrows(ErreurAnnuaire.DernierDomaine::class.java) { runBlocking { annuaire.supprimerDomaine(premier.id) } }
        val second = annuaire.creerDomaine("air-desktop-dictator")
        assertEquals(2, annuaire.domaines().size)
        annuaire.supprimerDomaine(premier.id)
        assertEquals(listOf(second), annuaire.domaines().map { it.id })
        assertEquals("air-desktop-dictator", annuaire.domaines().single().alias)
    }

    @Test
    fun lAliasDeDomaineSePoseSeRetireEtRefuseCeQueLAnnuaireRefuserait() = avecCompte { annuaire ->
        val d = annuaire.domaines().single().id
        annuaire.definirAliasDomaine(d, "Maison")
        assertEquals("Maison", annuaire.domaines().single().alias)
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) { runBlocking { annuaire.definirAliasDomaine(d, "a\"b") } }
        annuaire.definirAliasDomaine(d, null)
        assertNull(annuaire.domaines().single().alias)
    }

    @Test
    fun uneMachineSeRangeSeDeplaceEtSortDuDomaine() = avecCompte { annuaire ->
        val m = annuaire.declarerMachine("grenier", setOf(Capacite.ANNONCE)).id
        val d1 = annuaire.domaines().single().id
        val d2 = annuaire.creerDomaine(null)
        annuaire.rattacher(m, d1)
        assertEquals(listOf(m), annuaire.domaine(d1).machines.map { it.machine })
        annuaire.rattacher(m, d2)
        assertTrue(annuaire.domaine(d1).machines.isEmpty())
        assertEquals(d2, annuaire.domaineDe(m))
        annuaire.rattacher(m, null)
        assertNull(annuaire.domaineDe(m))
        // Supprimer un domaine détache ses machines.
        annuaire.rattacher(m, d2)
        annuaire.supprimerDomaine(d2)
        assertNull(annuaire.domaineDe(m))
    }

    @Test
    fun lAnnuaireLocalSInscritEstAccepteRecoitUnDomaineEtSaPaire() = avecCompte { annuaire ->
        val code = annuaire.declarerAnnuaire("speedy.air-desktop.org:6630")
        assertEquals(instant.plusSeconds(24 * 3600), code.expireA)
        assertEquals(EtatDInscription.Attendue, annuaire.annuairesLocaux().single().etat)
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) { runBlocking { annuaire.declarerAnnuaire("speedy") } }

        val speedy = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 7 })
        annuaire.presenter(code.code, speedy)
        assertEquals(EtatDInscription.EnAttente, annuaire.annuairesLocaux().single().etat)
        // Un non-administrateur ne voit pas les inscriptions : `404`, donc `null`.
        assertNull(annuaire.inscriptionsEnAttente())
        val d = annuaire.domaines().single().id
        // Tant qu'il n'est pas accepté, on ne lui confie rien.
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.confier(d, speedy) } }

        annuaire.administrerLesRacines()
        assertEquals(listOf(speedy), annuaire.inscriptionsEnAttente()!!.map { it.membre })
        annuaire.decider(speedy, accepte = true)
        assertTrue(annuaire.annuairesLocaux().single().estTitulaire)
        assertTrue(annuaire.inscriptionsEnAttente()!!.isEmpty())
        annuaire.confier(d, speedy)
        assertEquals(speedy, annuaire.domaines().single().hebergePar)

        // La paire : un second, puis plus de place.
        val helium = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 8 })
        val codeHelium = annuaire.declarerSecondMembre(speedy, "helium.air-desktop.org:6630")
        annuaire.presenter(codeHelium.code, helium)
        assertThrows(ErreurAnnuaire.PaireComplete::class.java) { runBlocking { annuaire.declarerSecondMembre(speedy, "tiers:6630") } }

        // Retirer l'annuaire rend ses domaines aux racines.
        annuaire.retirerAnnuaire(speedy)
        assertNull(annuaire.domaines().single().hebergePar)
        assertTrue(annuaire.annuairesLocaux().filter { it.membre != null }.all { it.etat == EtatDInscription.Retiree })
    }

    @Test
    fun leRefusLEmporteEtNeSeRenverseQueParUneInscriptionNeuve() = avecCompte { annuaire ->
        annuaire.administrerLesRacines()
        val membre = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 9 })
        annuaire.presenter(annuaire.declarerAnnuaire("speedy:6630").code, membre)
        annuaire.decider(membre, accepte = false)
        assertEquals(EtatDInscription.Refusee, annuaire.annuairesLocaux().single().etat)
        assertThrows(ErreurAnnuaire.InscriptionTranchee::class.java) { runBlocking { annuaire.decider(membre, accepte = true) } }
    }
}
