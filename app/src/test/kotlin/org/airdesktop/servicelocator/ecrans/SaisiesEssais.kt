package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Capacite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que chaque feuille enverrait, et quand « Enregistrer » reste éteint — la règle des feuilles du Mac
 * (`actionPermise`) : une saisie refusée ou qui ne change rien n'allume pas l'action.
 */
class SaisiesEssais {
    /** « é » décomposé : e + accent aigu combinant. L'annuaire range la forme composée. */
    private val eDecompose = "é"

    @Test
    fun unDomaineSeCreeSansAliasOuAvecUnAliasValide() {
        assertTrue(Saisies.creationDeDomaine(""))
        assertTrue(Saisies.creationDeDomaine("Maison de campagne"))
        assertFalse(Saisies.creationDeDomaine("guillemet \""))
        assertFalse(Saisies.creationDeDomaine("x".repeat(65)))
    }

    @Test
    fun lAliasDUnDomaineSEnregistreSIlChangeUneFoisNormalise() {
        assertEquals("Grenier", Saisies.aliasDeDomaine("Grenier", null))
        assertEquals("Grenier", Saisies.aliasDeDomaine("Grenier", "grenier"))
        assertNull(Saisies.aliasDeDomaine("Grenier", "Grenier"))
        // Le même alias, saisi décomposé : rien ne change, rien ne part.
        assertNull(Saisies.aliasDeDomaine("Cav${eDecompose}", "Cavé"))
        // Vider le champ n'est pas « Enregistrer » : le retrait est un autre geste.
        assertNull(Saisies.aliasDeDomaine("", "Grenier"))
    }

    @Test
    fun lAliasDUneMachineSuitLaMemeRegle() {
        assertEquals("Le serveur du grenier", Saisies.aliasDeMachine("Le serveur du grenier", null))
        assertNull(Saisies.aliasDeMachine("Le serveur du grenier", "Le serveur du grenier"))
        assertNull(Saisies.aliasDeMachine("", null))
        assertNull(Saisies.aliasDeMachine("x".repeat(254), null))
    }

    @Test
    fun leNomDHoteSEnvoieTelQueSaisiSIlEstValideEtAutre() {
        assertEquals("speedy-2", Saisies.nomDeMachine("speedy-2", "speedy"))
        assertEquals("Speedy", Saisies.nomDeMachine("Speedy", "speedy"))
        assertNull(Saisies.nomDeMachine("speedy", "speedy"))
        assertNull(Saisies.nomDeMachine("le grenier", "speedy"))
        assertNull(Saisies.nomDeMachine("-speedy", "speedy"))
        assertNull(Saisies.nomDeMachine("", "speedy"))
    }

    @Test
    fun lesCapacitesNePartentQueSiElleChangent() {
        val deux = setOf(Capacite.ANNONCE, Capacite.LECTURE)
        assertNull(Saisies.capacites(deux, deux))
        assertEquals(setOf(Capacite.LECTURE), Saisies.capacites(setOf(Capacite.LECTURE), deux))
        // Aucune capacité est un choix permis : la machine ne fait plus rien, et c'est voulu.
        assertEquals(emptySet<Capacite>(), Saisies.capacites(emptySet(), setOf(Capacite.ANNONCE)))
    }

    @Test
    fun lAliasPublicSePoseSeRetireEnVidantOuNeFaitRien() {
        assertEquals(Saisies.AliasDeCompte.Poser("Thierry"), Saisies.aliasDeCompte("Thierry", null))
        assertEquals(Saisies.AliasDeCompte.Poser("Thierry"), Saisies.aliasDeCompte("Thierry", "thierry"))
        assertNull(Saisies.aliasDeCompte("Thierry", "Thierry"))
        // Vider le champ retire l'alias qu'il y avait ; vider un champ vide ne fait rien.
        assertEquals(Saisies.AliasDeCompte.Retirer, Saisies.aliasDeCompte("", "Thierry"))
        assertNull(Saisies.aliasDeCompte("", null))
        // Trop court, ou un tiret en deuxième caractère : l'annuaire refuserait.
        assertNull(Saisies.aliasDeCompte("ab", null))
        assertNull(Saisies.aliasDeCompte("u-thierry", null))
    }
}
