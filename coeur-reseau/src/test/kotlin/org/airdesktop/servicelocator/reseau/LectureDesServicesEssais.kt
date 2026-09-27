package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /v1/machines/{m}/services`, tel que le serveur 0.32.0 l'écrit (décision 60), et tel qu'il l'écrivait avant. */
class LectureDesServicesEssais {
    /** Le corps relevé le 27/09 sur nitrogen pour la machine speedy, à l'octet près. */
    private val federe = """[{"service":"s-6AQ1BCA8SY1GVVMR0JMWXCA0AQ","nom":"essai-federation","etat":"annonce","annonce":{"service":"s-6AQ1BCA8SY1GVVMR0JMWXCA0AQ","keepalive_secondes":10,"inactivite_secondes":30,"vu_depuis":{"adresse":"2a01:cb19:d27:2f00:3ac9:86ff:fe47:9d54","port":33995},"derriere_nat":"non","joignabilite":[{"protocole":"tcp","port":8080,"verdict":"joignable","candidat":"[2a01:cb19:d27:2f00:3ac9:86ff:fe47:9d54]:8080","origine":"reflexif","a":1790545643327}]},"sonde_par":"n-7MSV5RPCXBZH25PQM4ZPE5X87P","sonde_locale":true},
 {"service":"s-17PNPAMTAGY9160FRMAJ6DR77K","nom":"essai-renvoi","etat":"parti","volontaire":null,"sonde_par":"n-7MSV5RPCXBZH25PQM4ZPE5X87P","sonde_locale":false}]"""

    @Test
    fun unServiceFedereDitQuiASondeEtSiCEtaitDeLInterieur() {
        val (vivant, parti) = LectureDesServices.services(federe)
        assertEquals("essai-federation", vivant.nom)
        assertTrue(vivant.etat is Service.Etat.Annonce)
        assertEquals("n-7MSV5RPCXBZH25PQM4ZPE5X87P", vivant.sondePar?.texte)
        assertTrue(vivant.sondeLocale)
        assertTrue(vivant.joignabilite.values.single() is Joignabilite.Joignable)
        assertEquals("[2a01:cb19:d27:2f00:3ac9:86ff:fe47:9d54]:33995", vivant.diagnostic?.vuDepuis)
        assertEquals("essai-renvoi", parti.nom)
        assertTrue(parti.etat is Service.Etat.Parti)
        assertEquals("n-7MSV5RPCXBZH25PQM4ZPE5X87P", parti.sondePar?.texte)
        assertFalse(parti.sondeLocale)
    }

    /** Un service que la racine tient elle-même, ou un annuaire d'avant 0.32.0 : ni l'un ni l'autre champ — comme avant. */
    @Test
    fun sansLesChampsCommeAvant() {
        val corps = """[{"service":"s-6AQ1BCA8SY1GVVMR0JMWXCA0AQ","nom":"depot","etat":"annonce","annonce":{"joignabilite":[{"protocole":"tcp","port":22,"verdict":"en_cours"}]}}]"""
        val service = LectureDesServices.services(corps).single()
        assertNull(service.sondePar)
        assertFalse(service.sondeLocale)
        assertEquals(Joignabilite.EnCours, service.joignabilite.values.single())
    }

    /** Une origine illisible n'invente rien, et une entrée illisible est sautée, pas fatale. */
    @Test
    fun uneOrigineIllisibleEstIgnoreeEtUneEntreeIllisibleSautee() {
        val corps = """[{"service":"pas un identifiant"},{"service":"s-6AQ1BCA8SY1GVVMR0JMWXCA0AQ","etat":"parti","sonde_par":"u-5884A5EE7THEKHBQ3BT0VPGJKN"}]"""
        val service = LectureDesServices.services(corps).single()
        assertNull(service.sondePar)
    }
}
