package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.reseau.RacineDAnnuaire
import org.airdesktop.servicelocator.reseau.RacineIdentifiee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que la liste des domaines dit d'une ligne — la règle et les mots de l'application iOS (PR iOS #30). */
class DomainesLisiblesEssais {
    private val id = Identifiant(Genre.DOMAINE, ByteArray(16) { 7 })
    private val moi = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 1 })

    private fun domaine(alias: String?, hebergePar: Identifiant? = null) = Domaine(id, moi, alias, hebergePar, emptySet())

    @Test
    fun sansAliasLIdentifiantEntierEtLeDire() {
        assertEquals("${id.texte} - pas d'alias", titreComplet(domaine(null)))
        assertEquals("Maison", titreComplet(domaine("Maison")))
        // Le titre d'un détail ou d'un menu : jamais l'abrégé.
        assertFalse(domaine(null).affiche.contains("…"))
    }

    @Test
    fun lesMotsDuDetail() {
        assertEquals("pas d'alias", TextesDomaines.pasDAlias)
        assertEquals("Propriétaire", TextesDomaines.proprietaire)
        assertEquals("vous", TextesDomaines.vous)
    }

    private val racines = listOf(
        RacineDAnnuaire(
            "asl-root.air-desktop.org:6630", "asl-root.air-desktop.org", "Automatique",
            listOf(
                RacineIdentifiee("n-0PWT8HZD80QMSPPDZ5CQXXYHQC", listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630")),
                RacineIdentifiee("n-3K3P6H252W8K9370QG1YYTWBWB", listOf("[2001:41d0:20a:900::1d32]:6630", "178.32.16.249:6630")),
            ),
        ),
        RacineDAnnuaire(
            "nitrogen.air-desktop.org:6630", "nitrogen.air-desktop.org", null,
            listOf(RacineIdentifiee("n-0PWT8HZD80QMSPPDZ5CQXXYHQC", listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"))),
        ),
    )

    /** Chaque racine une fois, même présente sous « Automatique » et sous son nom, avec ses adresses. */
    @Test
    fun lesRacinesSeDisentAvecLeursAdresses() {
        val hebergement = Hebergement.de(domaine(null), racines, emptyList())
        assertEquals("Hébergé par : les racines", hebergement.titre)
        assertEquals(listOf("nitrogen.air-desktop.org", "argon.air-desktop.org"), hebergement.serveurs.map { it.nom })
        assertEquals(listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"), hebergement.serveurs.first().adresses)
    }

    /** Un annuaire local : les adresses déclarées de ses membres, et d'aucun autre. Sans rien de connu, rien d'inventé. */
    @Test
    fun unAnnuaireLocalSeDitParLesAdressesDeSesMembres() {
        val n = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 3 })
        val second = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 4 })
        val locaux = listOf(
            Inscription(EtatDInscription.Acceptee, "acceptée", n, n, null, "[2001:db8::1]:6630", null),
            Inscription(EtatDInscription.Acceptee, "acceptée", second, n, null, "192.0.2.2:6630", null),
            Inscription(EtatDInscription.Attendue, "attendue", null, null, null, "192.0.2.9:6630", null),
        )
        val hebergement = Hebergement.de(domaine(null, hebergePar = n), racines, locaux)
        assertEquals("Hébergé par : l'annuaire ${n.texte}", hebergement.titre)
        assertEquals(listOf("[2001:db8::1]:6630", "192.0.2.2:6630"), hebergement.serveurs.flatMap { it.adresses })
        assertTrue(Hebergement.de(domaine(null, hebergePar = n), racines, emptyList()).serveurs.isEmpty())
    }
}
