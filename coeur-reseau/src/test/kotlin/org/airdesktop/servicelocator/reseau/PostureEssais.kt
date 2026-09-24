package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.airdesktop.servicelocator.modele.CodeInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * La posture que l'annuaire annonce, et ce que l'application en fait
 * (`protocole.md` §2.2).
 */
class PostureEssais {
    @Test
    fun lesTroisPosturesSeLisent() {
        assertEquals(Posture.Exigee, Posture.depuisTexte("required"))
        assertEquals(Posture.Facultative, Posture.depuisTexte("optional"))
        assertEquals(Posture.Invitation, Posture.depuisTexte("invitation"))
    }

    @Test
    fun ceQuiNEstPasDitNEstPasUneInvitation() {
        // **LE CAS QUI COMPTE EN PRODUCTION** : une racine d'avant la 0.16.0
        // ne rend que sa version. Demander un code là où personne n'en donne
        // fermerait la porte d'un annuaire ouvert.
        assertEquals(Posture.NonDite, Posture.depuisTexte(null))
        assertEquals(Posture.NonDite, Posture.depuisTexte(""))
        // Une valeur qu'on ne connaît pas vient d'une version plus récente : on
        // ne suppose rien, et ce n'est pas une erreur.
        assertEquals(Posture.NonDite, Posture.depuisTexte("quelque-chose-de-neuf"))
    }

    @Test
    fun leBancAnnonceSaPosture(): Unit = runBlocking {
        assertEquals(Posture.Facultative, AnnuaireSimule().annonce()?.posture)
        assertEquals(Posture.Invitation, AnnuaireSimule(posture = Posture.Invitation).annonce()?.posture)
    }

    @Test
    fun sousInvitationUnCodeValideOuvreLeCompteEtNeSertQuUneFois(): Unit = runBlocking {
        val annuaire = AnnuaireSimule(posture = Posture.Invitation, invitations = setOf("4K9M2P7R1T"))
        val code = CodeInvitation.analyser("4k9m2-p7r1t")!!
        assertNotNull(annuaire.ouvrirCompte(CleLogicielle(), code))

        // Consommer, c'est retirer : le même code sur un banc neuf ne rouvre rien.
        val second = AnnuaireSimule(posture = Posture.Invitation, invitations = setOf("4K9M2P7R1T"))
        second.ouvrirCompte(CleLogicielle(), code)
        assertThrows(ErreurAnnuaire.InvitationRefusee::class.java) {
            runBlocking { AnnuaireSimule(posture = Posture.Invitation, invitations = emptySet()).ouvrirCompte(CleLogicielle(), code) }
        }
    }

    @Test
    fun sousInvitationUnCodeAbsentOuInconnuEstRefuseDeLaMemeFacon(): Unit = runBlocking {
        val annuaire = AnnuaireSimule(posture = Posture.Invitation, invitations = setOf("4K9M2P7R1T"))
        // Sans code : la porte est fermée.
        assertThrows(ErreurAnnuaire.InvitationRefusee::class.java) {
            runBlocking { annuaire.ouvrirCompte(CleLogicielle(), null) }
        }
        // Avec un code que l'exploitant n'a pas émis : le MÊME refus. Distinguer
        // dirait à qui essaie des codes lesquels ont existé.
        assertThrows(ErreurAnnuaire.InvitationRefusee::class.java) {
            runBlocking { annuaire.ouvrirCompte(CleLogicielle(), CodeInvitation.analyser("00000-00000")!!) }
        }
    }

    @Test
    fun horsInvitationLeCodeNEstPasDemande(): Unit = runBlocking {
        // Nos racines tournent en `optional` : le compte s'ouvre sans rien taper.
        assertNotNull(AnnuaireSimule(posture = Posture.Facultative).ouvrirCompte(CleLogicielle()))
        assertNotNull(AnnuaireSimule(posture = Posture.NonDite).ouvrirCompte(CleLogicielle()))
    }
}
