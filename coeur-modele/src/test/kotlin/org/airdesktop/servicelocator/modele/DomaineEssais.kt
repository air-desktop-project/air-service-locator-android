package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsEssais {
    @Test
    fun lesDomainesDepuis0_23EtLesAnnuairesLocauxDepuis0_27() {
        assertFalse(Versions.auMoins("0.22.2", Versions.DOMAINES))
        assertTrue(Versions.auMoins("0.23.0", Versions.DOMAINES))
        assertTrue(Versions.auMoins("0.26.0", Versions.DOMAINES))
        assertFalse(Versions.auMoins("0.26.0", Versions.ANNUAIRES_LOCAUX))
        assertTrue(Versions.auMoins("0.27.0", Versions.ANNUAIRES_LOCAUX))
        assertTrue(Versions.auMoins("1.0.0", Versions.ANNUAIRES_LOCAUX))
    }

    @Test
    fun uneVersionIllisibleNePasseAucunSeuil() {
        assertFalse(Versions.auMoins(null, Versions.DOMAINES))
        assertFalse(Versions.auMoins("banc en mémoire", Versions.DOMAINES))
        assertFalse(Versions.auMoins("0.27", Versions.ANNUAIRES_LOCAUX))
        assertFalse(Versions.auMoins("0.x.0", Versions.DOMAINES))
    }
}

class AliasDeDomaineEssais {
    @Test
    fun unASoixanteQuatreOctetsApresNfcSensibleALaCasse() {
        assertEquals("Maison", Alias.pourDomaine("Maison"))
        assertEquals("air-desktop-dictator", Alias.pourDomaine("air-desktop-dictator"))
        assertEquals("é".repeat(32), Alias.pourDomaine("é".repeat(32)))
        assertNull(Alias.pourDomaine("é".repeat(33)))
        assertNull(Alias.pourDomaine(""))
        assertNull(Alias.pourDomaine("a\"b"))
        // Décomposé à la saisie, rangé composé.
        assertEquals("été", Alias.pourDomaine("été"))
    }
}

class AdresseDAnnuaireEssais {
    @Test
    fun hotePortCommeLAnnuaireLAdmet() {
        assertEquals("speedy.air-desktop.org:6630", Alias.adresseDAnnuaire("speedy.air-desktop.org:6630"))
        assertEquals("[2a01:cb19:d27:2f00::1]:6630", Alias.adresseDAnnuaire(" [2a01:cb19:d27:2f00::1]:6630 "))
        assertEquals("192.168.1.102:6630", Alias.adresseDAnnuaire("192.168.1.102:6630"))
        assertNull(Alias.adresseDAnnuaire("speedy"))
        assertNull(Alias.adresseDAnnuaire("speedy:0"))
        assertNull(Alias.adresseDAnnuaire("speedy:06630"))
        assertNull(Alias.adresseDAnnuaire("speedy:70000"))
        assertNull(Alias.adresseDAnnuaire("2a01::1:6630"))
        assertNull(Alias.adresseDAnnuaire("spe edy:6630"))
        assertNull(Alias.adresseDAnnuaire("spé:6630"))
        assertNull(Alias.adresseDAnnuaire("a\"b:6630"))
    }
}

class DomaineEssais {
    private val d = Identifiant(Genre.DOMAINE, ByteArray(16) { 1 })
    private val u = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 2 })

    @Test
    fun administrerEmporteRattacherEtVoirMaisPasLocaliser() {
        val admin = Domaine(d, u, null, null, setOf(Domaine.ADMINISTRER))
        assertTrue(admin.admetUneMachine)
        assertTrue(admin.peut(Domaine.VOIR))
        assertFalse(admin.peut(Domaine.LOCALISER))
        assertFalse(Domaine(d, u, null, null, setOf(Domaine.VOIR)).admetUneMachine)
        assertTrue(Domaine(d, u, null, null, setOf(Domaine.RATTACHER)).admetUneMachine)
    }

    @Test
    fun lAliasPasseDevantLIdentifiant() {
        assertEquals("Maison", Domaine(d, u, "Maison", null, emptySet()).affiche)
        assertEquals(d.texte, Domaine(d, u, null, null, emptySet()).affiche)
    }

    @Test
    fun unEtatInconnuNeSeConfondAvecAucun() {
        assertEquals(EtatDInscription.EnAttente, EtatDInscription.depuisMot("en attente"))
        assertEquals(EtatDInscription.Acceptee, EtatDInscription.depuisMot("acceptée"))
        assertEquals(EtatDInscription.Inconnu, EtatDInscription.depuisMot("suspendue"))
        assertEquals(EtatDInscription.Inconnu, EtatDInscription.depuisMot(""))
        assertEquals(EtatDInscription.Inconnu, EtatDInscription.depuisMot(null))
    }
}
