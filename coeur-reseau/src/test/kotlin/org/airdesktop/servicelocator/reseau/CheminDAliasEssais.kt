package org.airdesktop.servicelocator.reseau

import org.junit.Assert.assertEquals
import org.junit.Test

class CheminDAliasEssais {
    @Test
    fun unAliasAsciiSansCaractereDeCheminPrendLeChemin() {
        assertEquals("/v1/alias/thierry", cheminDAlias("thierry"))
        assertEquals("/v1/alias/th-ierry2", cheminDAlias("th-ierry2"))
        // Les majuscules restent dans le chemin : la 0.26.0 les y admet.
        assertEquals("/v1/alias/Thierry", cheminDAlias("Thierry"))
    }

    @Test
    fun toutAutreAliasPrendLeParametrePourcentEncode() {
        assertEquals("/v1/alias?alias=a%20b", cheminDAlias("a b"))
        assertEquals("/v1/alias?alias=a%2Fb", cheminDAlias("a/b"))
        assertEquals("/v1/alias?alias=a%3Fb", cheminDAlias("a?b"))
        assertEquals("/v1/alias?alias=a%25b", cheminDAlias("a%b"))
        assertEquals("/v1/alias?alias=a%23b", cheminDAlias("a#b"))
        assertEquals("/v1/alias?alias=%C3%89lodie", cheminDAlias("Élodie"))
        // Décomposé à la saisie, composé sur le fil : c'est la forme que l'annuaire range.
        assertEquals("/v1/alias?alias=%C3%89lodie", cheminDAlias("Élodie"))
    }

    @Test
    fun uneEspaceEstPourcentVingtJamaisPlus() {
        assertEquals("a%20b", pourcentEncoder("a b"))
        assertEquals("a%2Bb", pourcentEncoder("a+b"))
        assertEquals("%F0%9F%8F%A0", pourcentEncoder("🏠"))
        assertEquals("a-._~Z9", pourcentEncoder("a-._~Z9"))
        assertEquals("%25%26%3D%3F%2F", pourcentEncoder("%&=?/"))
    }
}
