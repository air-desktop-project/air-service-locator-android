package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ce que la page « Mon annuaire local » montre et permet, décidé comme le Mac (`AnnuaireLocalFenetreVue`) : une tuile
 * par titulaire, ses seconds, ce qu'il sert, et le geste en regard du second membre.
 */
class AnnuaireLocalEssais {
    private fun n(o: Int) = Identifiant(Genre.ANNUAIRE, ByteArray(16) { o.toByte() })
    private fun d(o: Int) = Identifiant(Genre.DOMAINE, ByteArray(16) { o.toByte() })
    private val moi = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 1 })
    private val autre = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 2 })
    private val speedy = n(7)
    private val helium = n(8)

    private fun inscription(etat: EtatDInscription, membre: Identifiant?, annuaire: Identifiant?, adresse: String = "[2001:db8::1]:6630") =
        Inscription(etat, etat.mot, membre, annuaire, null, adresse, if (membre == null) Instant.parse("2026-09-29T10:00:00Z") else null)

    private fun domaine(id: Identifiant, hebergePar: Identifiant?, proprietaire: Identifiant = moi) =
        Domaine(id, proprietaire, null, hebergePar, setOf(Domaine.ADMINISTRER))

    private val titulaire = inscription(EtatDInscription.Acceptee, speedy, speedy)

    @Test
    fun unTitulaireAccepteSansSecondInviteADeclarerEtAConfier() {
        val page = PageDAnnuaireLocal.de(listOf(titulaire), listOf(domaine(d(1), speedy), domaine(d(2), null)))
        val tuile = page.tuiles.single()
        assertEquals(speedy, tuile.annuaire)
        assertTrue(tuile.seconds.isEmpty())
        assertEquals(GesteDuSecond.Declarer, tuile.gesteDuSecond)
        assertTrue(tuile.confierPossible)
        // Les domaines servis sont ceux qu'il héberge, pas les autres.
        assertEquals(listOf(d(1)), tuile.servis.map { it.id })
        assertFalse(page.vide)
    }

    @Test
    fun unSecondPresenteSeRetireEtUnCodeQuiAttendNeLaisseAucunGeste() {
        val present = inscription(EtatDInscription.Acceptee, helium, speedy, "[2001:db8::2]:6630")
        assertEquals(GesteDuSecond.Retirer(helium), PageDAnnuaireLocal.de(listOf(titulaire, present), emptyList()).tuiles.single().gesteDuSecond)
        val enAttente = inscription(EtatDInscription.EnAttente, helium, speedy)
        assertEquals(GesteDuSecond.Retirer(helium), PageDAnnuaireLocal.de(listOf(titulaire, enAttente), emptyList()).tuiles.single().gesteDuSecond)
        // Un second déclaré, dont la machine n'a pas encore présenté le code : ni retirer, ni déclarer.
        val code = inscription(EtatDInscription.Attendue, null, speedy)
        val tuile = PageDAnnuaireLocal.de(listOf(titulaire, code), emptyList()).tuiles.single()
        assertEquals(listOf(code), tuile.seconds)
        assertEquals(GesteDuSecond.Aucun, tuile.gesteDuSecond)
        // Et il ne passe pas pour une déclaration de premier membre.
        assertTrue(PageDAnnuaireLocal.de(listOf(titulaire, code), emptyList()).declarations.isEmpty())
    }

    @Test
    fun unSecondRetireRendLaPlaceEtUnePaireRetireeDisparait() {
        val retire = inscription(EtatDInscription.Retiree, helium, speedy)
        val tuile = PageDAnnuaireLocal.de(listOf(titulaire, retire), emptyList()).tuiles.single()
        assertTrue(tuile.seconds.isEmpty())
        assertEquals(GesteDuSecond.Declarer, tuile.gesteDuSecond)

        val paireRetiree = listOf(inscription(EtatDInscription.Retiree, speedy, speedy), retire)
        assertTrue(PageDAnnuaireLocal.de(paireRetiree, emptyList()).vide)
    }

    @Test
    fun unTitulaireEnAttenteNeConfieRienNiNeDeclareDeSecond() {
        val tuile = PageDAnnuaireLocal.de(listOf(inscription(EtatDInscription.EnAttente, speedy, speedy)), emptyList()).tuiles.single()
        assertFalse(tuile.confierPossible)
        assertEquals(GesteDuSecond.Aucun, tuile.gesteDuSecond)
    }

    @Test
    fun lesDeclarationsQuiAttendentLeurMachineOntLeurTuile() {
        val declaration = inscription(EtatDInscription.Attendue, null, null)
        val page = PageDAnnuaireLocal.de(listOf(declaration), emptyList())
        assertTrue(page.tuiles.isEmpty())
        assertEquals(listOf(declaration), page.declarations)
        assertTrue(PageDAnnuaireLocal.de(emptyList(), emptyList()).vide)
    }

    @Test
    fun onNeConfieQueSesDomainesQueLAnnuaireNeSertPasDeja() {
        val domaines = listOf(domaine(d(1), speedy), domaine(d(2), null), domaine(d(3), n(9)), domaine(d(4), null, proprietaire = autre))
        assertEquals(listOf(d(2), d(3)), PageDAnnuaireLocal.aConfier(domaines, moi, speedy).map { it.id })
    }

    @Test
    fun uneDemandeDitSiElleOuvreUnePaireOuEnCompleteUne() {
        assertEquals("Titulaire d'une nouvelle paire", titreDeLaDemande(inscription(EtatDInscription.EnAttente, speedy, speedy)))
        assertEquals("Second membre d'une paire", titreDeLaDemande(inscription(EtatDInscription.EnAttente, helium, speedy)))
    }

    @Test
    fun lesMotsDuMacALaLettre() {
        assertEquals("Titulaire de la paire", TextesFederation.titulaire)
        assertEquals("Second membre", TextesFederation.secondMembre)
        assertEquals("aucun — la paire n'a pas de secours", TextesFederation.sansSecours)
        assertEquals("Confier un domaine…", TextesFederation.confierUnDomaine)
        assertEquals("Déclarer…", TextesFederation.declarerSecond)
        assertEquals("La paire reste, servie par son titulaire seul, sans secours.", TextesFederation.retraitDuSecond)
        assertEquals("Acceptée", TextesFederation.badge(titulaire))
        assertEquals("En attente de la décision des racines", TextesFederation.badge(inscription(EtatDInscription.EnAttente, speedy, speedy)))
    }

    @Test
    fun unCodeDitCeQuIlLuiResteOuQuIlEstExpire() {
        val maintenant = Instant.parse("2026-09-28T10:00:00Z")
        assertEquals("expire dans 23 h", TextesFederation.expire(maintenant.plusSeconds(23 * 3600 + 59 * 60), maintenant))
        assertEquals("expire dans 12 min", TextesFederation.expire(maintenant.plusSeconds(12 * 60 + 5), maintenant))
        assertEquals("expiré", TextesFederation.expire(maintenant.minusSeconds(1), maintenant))
    }
}
