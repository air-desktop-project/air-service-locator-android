package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.EtatDeLAnnuaire
import org.airdesktop.servicelocator.modele.EtatDeLaPaire
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.VoieDuMembre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ── L'état de l'annuaire et de sa paire (décisions 70 et 86) ─────────────

    private fun membre(o: Identifiant, voie: VoieDuMembre?, paire: EtatDeLaPaire? = null) =
        inscription(EtatDInscription.Acceptee, o, speedy).copy(voie = voie, paire = paire)

    private fun etat(vararg voies: VoieDuMembre?) =
        EtatDeLAnnuaire.de(voies.mapIndexed { i, v -> membre(if (i == 0) speedy else helium, v) })

    @Test
    fun lAnnuaireEstVivantDesQuUneVoieTient() {
        assertEquals(EtatDeLAnnuaire.Vivant, etat(VoieDuMembre.Ouverte))
        assertEquals(EtatDeLAnnuaire.Vivant, etat(VoieDuMembre.Ouverte, VoieDuMembre.Tombee))
        assertEquals(EtatDeLAnnuaire.Vivant, etat(VoieDuMembre.Tombee, VoieDuMembre.Ouverte))
        assertEquals(EtatDeLAnnuaire.Vivant, etat(null, VoieDuMembre.Ouverte))
        assertEquals(EtatDeLAnnuaire.Vivant, etat(VoieDuMembre.Inconnue, VoieDuMembre.Ouverte))
    }

    @Test
    fun lAnnuaireEstPartiQuandAucuneNeTientEtQuUneEstTombee() {
        assertEquals(EtatDeLAnnuaire.Parti, etat(VoieDuMembre.Tombee))
        assertEquals(EtatDeLAnnuaire.Parti, etat(VoieDuMembre.Tombee, VoieDuMembre.Tombee))
        assertEquals(EtatDeLAnnuaire.Parti, etat(VoieDuMembre.Tombee, null))
        assertEquals(EtatDeLAnnuaire.Parti, etat(VoieDuMembre.Inconnue, VoieDuMembre.Tombee))
    }

    @Test
    fun sansVoieConnueLaRacineNaPasDeNouvelles() {
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, etat(null))
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, etat(null, null))
        // Un mot inconnu ne dit ni ouverte ni tombée.
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, etat(VoieDuMembre.Inconnue))
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, etat(VoieDuMembre.Inconnue, null))
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, EtatDeLAnnuaire.de(emptyList()))
    }

    @Test
    fun laTuileTientSonEtatDeSesMembresEtNenDitRienAvantLAcceptation() {
        val vivante = PageDAnnuaireLocal.de(listOf(membre(speedy, VoieDuMembre.Tombee), membre(helium, VoieDuMembre.Ouverte)), emptyList())
        assertEquals(EtatDeLAnnuaire.Vivant, vivante.tuiles.single().etat)
        // Le second seul tient : l'annuaire vit par lui.
        val parSonSecond = PageDAnnuaireLocal.de(listOf(membre(speedy, null), membre(helium, VoieDuMembre.Ouverte)), emptyList())
        assertEquals(EtatDeLAnnuaire.Vivant, parSonSecond.tuiles.single().etat)
        assertEquals(EtatDeLAnnuaire.PasDeNouvelles, PageDAnnuaireLocal.de(listOf(titulaire), emptyList()).tuiles.single().etat)
        assertNull(PageDAnnuaireLocal.de(listOf(inscription(EtatDInscription.EnAttente, speedy, speedy)), emptyList()).tuiles.single().etat)
    }

    @Test
    fun lesLibellesPartagesAvecLeMac() {
        assertEquals("Vivant", TextesFederation.etat(EtatDeLAnnuaire.Vivant))
        assertEquals("Parti", TextesFederation.etat(EtatDeLAnnuaire.Parti))
        assertEquals("Pas de nouvelles", TextesFederation.etat(EtatDeLAnnuaire.PasDeNouvelles))
        assertEquals("Voie ouverte", TextesFederation.voie(VoieDuMembre.Ouverte))
        assertEquals("Voie tombée", TextesFederation.voie(VoieDuMembre.Tombee))
        assertEquals("—", TextesFederation.voie(VoieDuMembre.Inconnue))
        assertEquals("—", TextesFederation.voie(null))
        assertEquals("Paire réglée", TextesFederation.paireReglee)
        assertEquals("Paire mal réglée", TextesFederation.paireMalReglee)
    }

    @Test
    fun uneRepliqueQuiNePassePasSeDitEtRienNeSeDitSinon() {
        assertEquals(
            "helium tourne sans --peer : la paire ne se réplique pas ; réglez --peer et --peer-key sur cette machine.",
            TextesFederation.avertissement(EtatDeLaPaire.SansPeer, "helium"),
        )
        assertTrue(TextesFederation.avertissement(EtatDeLaPaire.PeerInconnu, "helium")!!.contains("--peer de helium"))
        for (paire in listOf(EtatDeLaPaire.Seul, EtatDeLaPaire.Reglee, EtatDeLaPaire.Inconnu, null)) {
            assertNull(TextesFederation.avertissement(paire, "helium"))
        }
        assertTrue(EtatDeLaPaire.SansPeer.malReglee && EtatDeLaPaire.PeerInconnu.malReglee)
        assertFalse(EtatDeLaPaire.Seul.malReglee || EtatDeLaPaire.Reglee.malReglee || EtatDeLaPaire.Inconnu.malReglee)
    }

    @Test
    fun lHoteDUneAdresseNommeLaMachine() {
        assertEquals("helium.example", hoteDe("helium.example:6630"))
        assertEquals("2001:db8::1", hoteDe("[2001:db8::1]:6630"))
        assertEquals("192.0.2.1", hoteDe("192.0.2.1:6630"))
        assertEquals("2001:db8::1", hoteDe("2001:db8::1"))
        assertEquals("helium", hoteDe("helium"))
    }
}
