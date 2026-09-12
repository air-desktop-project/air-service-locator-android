package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que deux téléphones s'échangent, et le base32 qui le porte. */
class InvitationEssais {
    @Test
    fun leBase32FaitLAllerRetourSurTrenteTroisOctets() {
        val octets = ByteArray(33) { if (it == 0) 0x02 else (it * 7).toByte() }
        val texte = Crockford.texte(octets)
        assertEquals(53, texte.length)
        assertArrayEquals(octets, Crockford.octets(texte, 33))
        // Un octet, deux octets : le bourrage change, le résultat non.
        for (taille in 1..40) {
            val quelconque = ByteArray(taille) { (it * 37 + 11).toByte() }
            assertArrayEquals(quelconque, Crockford.octets(Crockford.texte(quelconque), taille))
        }
    }

    @Test
    fun leBase32RefuseCeQuiNestPasUnCode() {
        val texte = Crockford.texte(ByteArray(33) { 0xFF.toByte() })
        assertNull(Crockford.octets(texte.dropLast(1), 33))
        assertNull(Crockford.octets(texte + "0", 33))
        assertNull(Crockford.octets("U".repeat(53), 33))
        // Un bit de bourrage à un : ce n'est pas l'écriture canonique.
        assertNull(Crockford.octets("Z" + texte.drop(1), 33))
        // La casse et les confusions de Crockford sont rattrapées.
        assertArrayEquals(Crockford.octets(texte, 33), Crockford.octets(texte.lowercase(), 33))
    }

    @Test
    fun uneInvitationSeLitTelleQuElleSEcrit() {
        val cle = CleLogicielle().clePublique
        val montree = Invitation.Cle(cle)
        assertTrue(montree.texte.startsWith("asl:cle:"))
        assertEquals(montree, Invitation.analyser(montree.texte))
        assertEquals(montree, Invitation.analyser(" " + montree.texte.lowercase() + "\n"))

        val compte = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 3 })
        val appareil = Identifiant(Genre.APPAREIL, ByteArray(16) { 9 })
        val reponse = Invitation.Appareil(compte, appareil)
        assertEquals("asl:appareil:${compte.texte}:${appareil.texte}", reponse.texte)
        assertEquals(reponse, Invitation.analyser(reponse.texte))

        // Un QR étranger, une clé hors forme, des genres inversés : rien.
        assertNull(Invitation.analyser("https://example.org"))
        assertNull(Invitation.analyser("asl:cle:" + Crockford.texte(ByteArray(33) { 0x04 })))
        assertNull(Invitation.analyser("asl:appareil:${appareil.texte}:${compte.texte}"))
    }
}
