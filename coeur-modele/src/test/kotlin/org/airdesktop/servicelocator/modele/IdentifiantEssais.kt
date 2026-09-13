package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** La grammaire des identifiants, telle que `asl-id` l'arrête côté serveur. */
class IdentifiantEssais {
    @Test
    fun unTexteCanoniqueSeRelitTelQuel() {
        val identifiant = Identifiant(Genre.MACHINE, ByteArray(16) { (it * 17).toByte() })
        val texte = identifiant.texte
        assertEquals(28, texte.length)
        assertTrue(texte.startsWith("m-"))
        assertEquals(identifiant, Identifiant.analyser(texte))
    }

    @Test
    fun lesConfusionsDeCrockfordSontRattrapees() {
        val identifiant = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 1 })
        val texte = identifiant.texte.replace('1', 'l').replace('0', 'O').lowercase()
        assertEquals(identifiant, Identifiant.analyser(texte))
    }

    @Test
    fun leGenreEstVerifieALaLecture() {
        val texte = Identifiant(Genre.MACHINE, ByteArray(16)).texte
        assertThrows(Identifiant.Erreur.GenreInattendu::class.java) { Identifiant.analyser(texte, Genre.UTILISATEUR) }
    }

    @Test
    fun ceQuiNestPasUnIdentifiantEstRefuse() {
        assertThrows(Identifiant.Erreur.Longueur::class.java) { Identifiant.analyser("u-1") }
        assertThrows(Identifiant.Erreur.PrefixeInconnu::class.java) { Identifiant.analyser("x-00000000000000000000000000") }
        assertThrows(Identifiant.Erreur.SeparateurAbsent::class.java) { Identifiant.analyser("u_00000000000000000000000000") }
        assertThrows(Identifiant.Erreur.SymboleInvalide::class.java) { Identifiant.analyser("u-000U0000000000000000000000") }
        // Un premier symbole ≥ 8 : 130 bits ne tiennent pas dans 128.
        assertThrows(Identifiant.Erreur.Debordement::class.java) { Identifiant.analyser("u-80000000000000000000000000") }
    }

    @Test
    fun deuxTextesDifferentsPeuventDesignerLeMemeIdentifiant() {
        val a = Identifiant.analyser("u-0123456789ABCDEFGHJKMNPQRS")
        val b = Identifiant.analyser("u-0i23456789abcdefghjkmnpqrs")
        assertEquals(a, b)
        assertEquals("u-0123456789ABCDEFGHJKMNPQRS", a.texte)
    }
}

class CodeEnrolementEssais {
    @Test
    fun dixSymbolesGroupesParCinq() {
        val code = CodeEnrolement.depuisEntropie(ByteArray(8) { 0xFF.toByte() }, Instant.EPOCH)
        assertEquals("ZZZZZZZZZZ", code.symboles)
        assertEquals("ZZZZZ-ZZZZZ", code.texteGroupe)
        assertEquals("asl enrole ZZZZZ-ZZZZZ", code.commande)
    }

    @Test
    fun laMemeEntropieQueLeServeur() {
        // `asl_cle::CodeEnrolement::depuis_entropie` : `u64::from_be_bytes >> 14`, puis cinq bits par symbole.
        val code = CodeEnrolement.depuisEntropie(byteArrayOf(0, 0, 0, 0, 0, 0, 0x40, 0), Instant.EPOCH)
        assertEquals("0000000001", code.symboles)
    }

    @Test
    fun dixMinutesEtPasUneDePlus() {
        val emis = Instant.ofEpochSecond(1_000_000)
        val code = CodeEnrolement.depuisEntropie(ByteArray(8), emis)
        assertTrue(code.estValide(emis.plusSeconds(599)))
        assertFalse(code.estValide(emis.plusSeconds(600)))
        assertTrue(code.reste(emis.plusSeconds(700)).isZero)
    }
}

class NomDeMachineEssais {
    @Test
    fun toutLutf8SaufCeQuiRetourneSesVoisins() {
        assertTrue(Machine.nomValide("grenier"))
        assertTrue(Machine.nomValide("serveur été 🏠"))
        assertFalse(Machine.nomValide(""))
        assertFalse(Machine.nomValide("é".repeat(33)))  // 66 octets
        assertFalse(Machine.nomValide("a\"b"))
        assertFalse(Machine.nomValide("a\\b"))
        assertFalse(Machine.nomValide("a\tb"))
        assertFalse(Machine.nomValide("a‮b"))
        assertFalse(Machine.nomValide("﻿a"))
    }
}
