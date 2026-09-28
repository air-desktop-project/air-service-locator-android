package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Retirer le second membre d'une paire — `DELETE /v1/annuaires/{n}/membres/{n2}` : la requête que le transport
 * réel envoie, ce qu'il fait de la réponse, et la règle que le banc tient.
 */
class RetraitDUnMembreEssais {
    private val speedy = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 7 })
    private val helium = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 8 })

    @Test
    fun laRequeteNommeLAnnuairePuisLeMembreSousLeurFormeCanonique() {
        val (methode, chemin) = RetraitDUnMembre.requete(speedy, helium)
        assertEquals("DELETE", methode)
        assertEquals("/v1/annuaires/${speedy.texte}/membres/${helium.texte}", chemin)
        assertTrue(chemin.startsWith("/v1/annuaires/n-"))
    }

    @Test
    fun deuxCentQuatreCestFaitEtLeRestePrendLesMotsDuMac() {
        assertNull(RetraitDUnMembre.verdict(204))
        // `404` : un membre d'un autre annuaire, un annuaire qui n'est pas à moi, un membre déjà retiré.
        assertSame(ErreurAnnuaire.Introuvable, RetraitDUnMembre.verdict(404))
        assertEquals("Introuvable.", RetraitDUnMembre.verdict(404)!!.message)
        assertSame(ErreurAnnuaire.NonReconnu, RetraitDUnMembre.verdict(401))
        assertEquals("Demande refusée : l'annuaire a répondu 500.", RetraitDUnMembre.verdict(500)!!.message)
        // Même un `200` n'est pas le `204` promis : on ne prétend pas que c'est fait.
        assertTrue(RetraitDUnMembre.verdict(200) is ErreurAnnuaire.RequeteInvalide)
    }

    /** Un banc avec une paire acceptée : speedy titulaire, helium second, un domaine confié. */
    private fun avecUnePaire(bloc: suspend (AnnuaireSimule) -> Unit) = runBlocking {
        val annuaire = AnnuaireSimule { Instant.parse("2026-09-28T10:00:00Z") }
        annuaire.ouvrirCompte(CleLogicielle())
        annuaire.administrerLesRacines()
        annuaire.presenter(annuaire.declarerAnnuaire("[2001:db8::1]:6630").code, speedy)
        annuaire.decider(speedy, accepte = true)
        annuaire.presenter(annuaire.declarerSecondMembre(speedy, "[2001:db8::2]:6630").code, helium)
        annuaire.decider(helium, accepte = true)
        annuaire.confier(annuaire.domaines().single().id, speedy)
        bloc(annuaire)
    }

    @Test
    fun retirerLeSecondLaisseLaPaireEtSesDomaines() = avecUnePaire { annuaire ->
        annuaire.retirerMembre(speedy, helium)
        val locaux = annuaire.annuairesLocaux()
        assertEquals(EtatDInscription.Acceptee, locaux.single { it.membre == speedy }.etat)
        assertEquals(EtatDInscription.Retiree, locaux.single { it.membre == helium }.etat)
        // La paire reste, servie par son titulaire seul : le domaine ne revient pas aux racines.
        assertEquals(speedy, annuaire.domaines().single().hebergePar)
        // Et la place se libère : un nouveau second se déclare.
        annuaire.declarerSecondMembre(speedy, "[2001:db8::3]:6630")
    }

    @Test
    fun nommerLeTitulaireRetireLaPaireEntiere() = avecUnePaire { annuaire ->
        annuaire.retirerMembre(speedy, speedy)
        assertTrue(annuaire.annuairesLocaux().filter { it.membre != null }.all { it.etat == EtatDInscription.Retiree })
        assertNull(annuaire.domaines().single().hebergePar)
    }

    @Test
    fun unMembreDUnAutreAnnuaireEstIntrouvable() = avecUnePaire { annuaire ->
        val etranger = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 9 })
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.retirerMembre(speedy, etranger) } }
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.retirerMembre(etranger, helium) } }
    }
}
