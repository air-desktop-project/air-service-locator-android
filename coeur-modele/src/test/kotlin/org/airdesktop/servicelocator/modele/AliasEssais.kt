package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasDeMachineEssais {
    @Test
    fun toutLutf8SaufCeQueLAnnuaireRefuse() {
        assertEquals("Serveur été 🏠", Alias.pourMachine("Serveur été 🏠"))
        assertEquals("grenier.maison.example", Alias.pourMachine("grenier.maison.example"))
        assertNull(Alias.pourMachine(""))
        assertNull(Alias.pourMachine("a\"b"))
        assertNull(Alias.pourMachine("a\\b"))
        assertNull(Alias.pourMachine("a\tb"))
        assertNull(Alias.pourMachine("a‮b"))
        assertNull(Alias.pourMachine("﻿a"))
    }

    @Test
    fun laBorneSeMesureEnOctetsApresNfc() {
        assertEquals(253, Alias.pourMachine("a".repeat(253))?.length)
        assertNull(Alias.pourMachine("a".repeat(254)))
        // « é » décomposé fait trois octets, composé deux : 127 fois, 381 octets tapés, 254 rangés.
        assertNull(Alias.pourMachine("é".repeat(127)))
        // 126 fois : 252 octets rangés, c'est admis — et c'est la forme composée qui part.
        assertEquals("é".repeat(126), Alias.pourMachine("é".repeat(126)))
    }

    @Test
    fun laCasseCompte() {
        assertNotEquals(Alias.pourMachine("Maison"), Alias.pourMachine("maison"))
    }
}

class AliasDeCompteEssais {
    @Test
    fun troisATrenteDeuxOctetsSensibleALaCasse() {
        assertEquals("Thierry", Alias.pourCompte("Thierry"))
        assertEquals("thierry", Alias.pourCompte("thierry"))
        assertEquals("Élodie", Alias.pourCompte("Élodie"))
        assertNull(Alias.pourCompte("ab"))
        assertEquals("abc", Alias.pourCompte("abc"))
        assertNull(Alias.pourCompte("a".repeat(33)))
        assertEquals("é".repeat(16), Alias.pourCompte("é".repeat(16)))  // 32 octets
        assertNull(Alias.pourCompte("é".repeat(17)))                    // 34 octets
        assertNull(Alias.pourCompte("th\"erry"))
    }

    @Test
    fun pasDeTiretEnDeuxiemePosition() {
        // « u-… » est un identifiant : tapé dans le même champ, il ne doit pas pouvoir passer pour un alias.
        assertNull(Alias.pourCompte("u-thierry"))
        assertNull(Alias.pourCompte("é-lodie"))
        assertEquals("-thierry", Alias.pourCompte("-thierry"))
        assertEquals("th-ierry", Alias.pourCompte("th-ierry"))
    }

    @Test
    fun laSaisieAvantNfcEstBornee() {
        // 256 octets saisis : refusé avant même le NFC, comme l'annuaire.
        assertNull(Alias.pourCompte("a".repeat(256)))
        assertNull(Alias.pourMachine("a".repeat(481)))
        // 480 octets saisis de « e » + accent combinant (3 octets) : 160 fois, 320 octets rangés — trop long après NFC.
        assertNull(Alias.pourMachine("e\u0301".repeat(160)))
    }
}

class VersionDAliasDeMachineEssais {
    @Test
    fun leChampNApparaitQueDepuisLaVersionQuiLeRange() {
        assertEquals(true, Alias.aliasDeMachineAdmis("0.26.0"))
        assertEquals(true, Alias.aliasDeMachineAdmis("0.27.3"))
        assertEquals(true, Alias.aliasDeMachineAdmis("1.0.0"))
        assertEquals(false, Alias.aliasDeMachineAdmis("0.25.0"))
        assertEquals(false, Alias.aliasDeMachineAdmis("banc en mémoire"))
        assertEquals(false, Alias.aliasDeMachineAdmis("0.26"))
        assertEquals(false, Alias.aliasDeMachineAdmis(null))
    }
}
