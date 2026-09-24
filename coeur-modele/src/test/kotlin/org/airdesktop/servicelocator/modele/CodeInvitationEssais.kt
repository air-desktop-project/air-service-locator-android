package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ce qu'un humain tape, et ce qui part sur le fil (`protocole.md` §2.2). */
class CodeInvitationEssais {
    @Test
    fun laFormeCanoniqueEtLaFormeGroupeeDonnentLeMemeCode() {
        val canonique = CodeInvitation.analyser("4K9M2P7R1T")
        val groupe = CodeInvitation.analyser("4K9M2-P7R1T")
        assertEquals("4K9M2P7R1T", canonique?.symboles)
        assertEquals(canonique, groupe)
        // Le tiret n'ajoute rien à taper : c'est un séparateur pour l'œil.
        assertEquals("4K9M2-P7R1T", canonique?.texteGroupe)
    }

    @Test
    fun laCasseEtLesConfusionsDeCrockfordSontRattrapees() {
        // `O` pour zéro, `I` et `L` pour un : l'annuaire les rattrape aussi, et
        // faire échouer quelqu'un sur la forme d'une lettre n'apprendrait rien.
        assertEquals("0123456789", CodeInvitation.analyser("oi23456789")?.symboles)
        assertEquals("0123456789", CodeInvitation.analyser("Ol23456789")?.symboles)
        assertEquals("4K9M2P7R1T", CodeInvitation.analyser("4k9m2-p7r1t")?.symboles)
    }

    @Test
    fun lesEspacesAutourNeComptentPas() {
        // Un code collé depuis un message en traîne souvent.
        assertEquals("4K9M2P7R1T", CodeInvitation.analyser("  4K9M2-P7R1T  ")?.symboles)
    }

    @Test
    fun ceQuiNEstPasUnCodeNEnEstPasUn() {
        assertNull("trop court", CodeInvitation.analyser("4K9M2"))
        assertNull("trop long", CodeInvitation.analyser("4K9M2P7R1TX"))
        assertNull("vide", CodeInvitation.analyser(""))
        // `U` n'est pas de l'alphabet de Crockford, et n'est pas une confusion rattrapée.
        assertNull("symbole hors alphabet", CodeInvitation.analyser("4K9M2P7R1U"))
    }

    @Test
    fun cequiPartFaitDixOctetsEtCeSontLesSymboles() {
        // **LA CONTRAINTE DU FIL** : l'annuaire refuse tout ce qui n'occupe pas
        // dix octets dans la case d'attestation, et relit ce texte pour en
        // prendre l'empreinte. Le tiret d'affichage en ferait onze.
        val code = CodeInvitation.analyser("4k9m2-p7r1t")!!
        assertEquals(CodeInvitation.NOMBRE_SYMBOLES, code.octets.size)
        assertArrayEquals("4K9M2P7R1T".toByteArray(Charsets.US_ASCII), code.octets)
    }
}
