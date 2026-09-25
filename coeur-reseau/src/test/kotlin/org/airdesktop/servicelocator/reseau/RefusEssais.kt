package org.airdesktop.servicelocator.reseau

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RefusEssais {
    @Test
    fun sousInvitationTropDEssaisNeSeConfondPlusAvecUnCodeRefuse() {
        // Le client rend désormais -13 pour un 429 : attendre, pas changer de code.
        assertSame(ErreurAnnuaire.TropDEssais, refusDInvitation(Natif.TROP_D_ESSAIS))
        // Un 403 garde sa phrase — code faux, expiré ou déjà servi.
        assertSame(ErreurAnnuaire.InvitationRefusee, refusDInvitation(Natif.REFUSE))
        assertNotEquals(ErreurAnnuaire.TropDEssais.message, ErreurAnnuaire.InvitationRefusee.message)
        // Ce qui n'est pas un refus suit le chemin commun.
        assertNull(refusDInvitation(Natif.SIGNATURE_REFUSEE))
        assertNull(refusDInvitation(Natif.INJOIGNABLE))
    }

    @Test
    fun laPhraseDuRefusDInvitationNeParlePlusDAttendre() {
        // Avant, faute de pouvoir distinguer, la même phrase disait aussi d'attendre.
        assertTrue(ErreurAnnuaire.TropDEssais.message!!.contains("minute"))
        assertTrue(!ErreurAnnuaire.InvitationRefusee.message!!.contains("minute"))
    }

    @Test
    fun rejoindreDitDAttendreSurTropDEssaisEtGardeSesAutresPhrases() {
        val attente = refusDeRejoindre(Natif.TROP_D_ESSAIS)
        assertTrue(attente is ErreurAnnuaire.ARecommencer)
        assertTrue(attente!!.message!!.contains("attendez une minute"))
        assertTrue(refusDeRejoindre(Natif.CHAINE_REFUSEE)!!.message!!.contains("attestation"))
        assertTrue(refusDeRejoindre(Natif.REFUSE)!!.message!!.contains("refusé la preuve"))
        assertNull(refusDeRejoindre(-99))
    }
}
