package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey

/** Ce qu'un appareil signe, et comment — octet pour octet comme `asl-cle`. */
class MessagesEssais {
    private val appareil = Identifiant(Genre.APPAREIL, ByteArray(16) { it.toByte() })
    private val defi = ByteArray(32) { 0xAA.toByte() }
    private val liaison = ByteArray(32) { 0xBB.toByte() }

    @Test
    fun lesDomainesSontCeuxDuServeurTerminesParUnZero() {
        assertArrayEquals("air-service-locator/v1/authentification-machine".toByteArray() + 0, Messages.DOMAINE_AUTHENTIFICATION)
        assertArrayEquals("air-service-locator/v1/possession-de-cle".toByteArray() + 0, Messages.DOMAINE_POSSESSION)
        assertArrayEquals("air-service-locator/v1/attestation-d-appareil".toByteArray() + 0, Messages.DOMAINE_ATTESTATION)
    }

    @Test
    fun leMessageDauthentificationEstDomaineGenreIdentifiantDefiLiaison() {
        val m = Messages.aSigner(appareil, defi, liaison)
        val d = Messages.DOMAINE_AUTHENTIFICATION
        assertEquals(d.size + 1 + 16 + 32 + 32, m.size)
        assertArrayEquals(d, m.copyOfRange(0, d.size))
        assertEquals('a'.code.toByte(), m[d.size])
        assertArrayEquals(appareil.octets, m.copyOfRange(d.size + 1, d.size + 17))
        assertArrayEquals(defi, m.copyOfRange(d.size + 17, d.size + 49))
        assertArrayEquals(liaison, m.copyOfRange(d.size + 49, m.size))
    }

    @Test
    fun leMessageDePossessionPorteLaCleEtNonUnNom() {
        val cle = CleLogicielle().clePublique
        val m = Messages.dePossession(cle, defi, liaison)
        assertEquals(Messages.DOMAINE_POSSESSION.size + 33 + 32 + 32, m.size)
        assertArrayEquals(cle, m.copyOfRange(Messages.DOMAINE_POSSESSION.size, Messages.DOMAINE_POSSESSION.size + 33))
    }
}

class P256Essais {
    @Test
    fun laClePubliqueEstSEC1CompresseeSurTrenteTroisOctetsEtSeDecompresse() {
        val cle = CleLogicielle()
        assertEquals(33, cle.clePublique.size)
        assertTrue(cle.clePublique[0] == 0x02.toByte() || cle.clePublique[0] == 0x03.toByte())
        val publique = P256.decompresser(cle.clePublique) as ECPublicKey
        assertArrayEquals(cle.clePublique, P256.compresser(publique))
        // Un préfixe qui n'est ni 02 ni 03, ou un x hors de la courbe, est refusé à la lecture.
        assertNull(P256.decompresser(byteArrayOf(0x04) + cle.clePublique.copyOfRange(1, 33)))
        assertNull(P256.decompresser(ByteArray(33) { 0xFF.toByte() }.also { it[0] = 0x02 }))
        assertNotNull(P256.decompresser(cle.clePublique))
    }

    @Test
    fun uneSignatureEstRSSurSoixanteQuatreOctetsEtVerifieSousLaCle() {
        val cle = CleLogicielle()
        val message = "un message quelconque".toByteArray()
        val signature = cle.signerSync(message)
        assertEquals(64, signature.size)
        assertTrue(P256.verifie(cle.clePublique, message, signature))
        assertFalse(P256.verifie(cle.clePublique, message + 0, signature))
        assertFalse(P256.verifie(CleLogicielle().clePublique, message, signature))
        assertFalse(P256.verifie(cle.clePublique, message, signature.copyOfRange(0, 63)))
    }

    @Test
    fun derEtRSSontLesDeuxEcrituresDuMemeCouple() {
        // Un r à bit de poids fort à 1 prend un zéro de signe en DER ; un s court n'en prend pas.
        val r = ByteArray(32) { 0x80.toByte() }
        val s = ByteArray(32).also { it[31] = 5 }
        val der = P256.plierDER(r + s)
        assertEquals(0x30.toByte(), der[0])
        assertEquals(0x02.toByte(), der[2]); assertEquals(33, der[3].toInt())  // 0x00 ‖ r
        assertArrayEquals(r + s, P256.deplierDER(der))
        assertArrayEquals(der, P256.plierDER(P256.deplierDER(der)))
    }

    /** Le serveur vérifie avec `p256` en hachant le message en SHA-256 : on s'assure que Java fait de même, via NONEwithECDSA sur le condensat. */
    @Test
    fun laSignatureCouvreLeSHA256DuMessage() {
        val cle = CleLogicielle()
        val message = "air-service-locator".toByteArray()
        val signature = cle.signerSync(message)
        val condensat = MessageDigest.getInstance("SHA-256").digest(message)
        val ok = Signature.getInstance("NONEwithECDSA").run {
            initVerify(P256.decompresser(cle.clePublique))
            update(condensat)
            verify(P256.plierDER(signature))
        }
        assertTrue(ok)
    }
}
