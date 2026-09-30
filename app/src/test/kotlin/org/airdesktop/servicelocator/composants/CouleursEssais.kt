package org.airdesktop.servicelocator.composants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux jeux de teintes d'état, valeur par valeur, et la règle qui les sépare : les vifs pour un disque plein, les
 * encres pour ce qui s'écrit. Les valeurs sont celles de l'application iOS/macOS (PR iOS #47, `Couleurs.swift`) — une
 * teinte qui change change des deux côtés.
 *
 * Ce qui demande un écran — le badge, la pastille, la bascule clair/sombre au moment d'afficher — n'est pas ici : la
 * table l'est, et c'est par elle que tout passe ([Couleurs.Texte.encre]).
 */
class CouleursEssais {
    /** Le fond que le thème peint réellement (`surface`, palette claire) : c'est sur lui que les mots se lisent. */
    private val fondClair = Color(0xFFF9F9FC)

    /** Les trois vifs, ceux des boutons de fenêtre de macOS — fermer, réduire, plein écran. */
    private val vifs = listOf(Couleurs.alerte, Couleurs.attention, Couleurs.joignable)

    @Test
    fun lesVifsSontCeuxDesBoutonsDeFenetreDeMacOS() {
        assertEquals(Color(0xFF28C840), Couleurs.joignable)
        assertEquals(Color(0xFFFEBC2E), Couleurs.attention)
        assertEquals(Color(0xFFFF5F57), Couleurs.alerte)
    }

    /** L'accent et le gris de « parti » ne bougent pas : ce ne sont pas des états à trois couleurs. */
    @Test
    fun laccentEtLeGrisNeChangentPas() {
        assertEquals(Color(0xFF2D6BB0), Couleurs.accent)
        assertEquals(Color(0xFF73777F), Couleurs.parti)
    }

    @Test
    fun lesEncresDunFondClairSontLesVifsAssombris() {
        assertEquals(Color(0xFF1C8C2C), Couleurs.Texte.surClair(Couleurs.joignable))
        assertEquals(Color(0xFF9A6900), Couleurs.Texte.surClair(Couleurs.attention))
        assertEquals(Color(0xFFC42B24), Couleurs.Texte.surClair(Couleurs.alerte))
    }

    /** Sur fond sombre, ce sont les vifs qui se lisent : l'encre est alors la couleur elle-même. */
    @Test
    fun surFondSombreLencreEstLeVif() {
        vifs.forEach { assertEquals(it, Couleurs.Texte.encre(it, fondSombre = true)) }
    }

    /** **La règle** : aucun des trois vifs ne s'écrit sur un fond clair. */
    @Test
    fun aucunVifNeSecritSurUnFondClair() {
        vifs.forEach { vif ->
            val encre = Couleurs.Texte.encre(vif, fondSombre = false)
            assertNotEquals("le vif $vif ne doit pas s'écrire sur un fond clair", vif, encre)
            assertTrue("l'encre de $vif doit être plus sombre que lui", encre.luminance() < vif.luminance())
        }
    }

    /**
     * Pourquoi le second jeu existe : sur le fond du thème, un mot écrit en vif n'atteint même pas le contraste de 3
     * pour 1 — et chaque encre le dépasse. Le seuil sépare les deux jeux exactement (l'orange vif tombe à 1,6).
     */
    @Test
    fun leVifNeSeLitPasSurLeFondDuTheme() {
        vifs.forEach { vif ->
            assertTrue("$vif ne se lit pas sur un fond clair", contraste(vif, fondClair) < 3.0)
            val encre = Couleurs.Texte.encre(vif, fondSombre = false)
            assertTrue("l'encre de $vif doit se lire sur le fond du thème", contraste(encre, fondClair) >= 3.0)
        }
    }

    /** Une couleur qui n'est pas un état passe sans être touchée : elle se lisait déjà. */
    @Test
    fun cequiNestPasUnEtatNeSassombritPas() {
        listOf(Couleurs.accent, Couleurs.parti, Couleurs.nonSonde, fondClair).forEach {
            assertEquals(it, Couleurs.Texte.encre(it, fondSombre = false))
            assertEquals(it, Couleurs.Texte.encre(it, fondSombre = true))
        }
    }

    /** Le rapport de contraste de WCAG 2, tel que l'on juge si un mot se lit sur son fond. */
    private fun contraste(a: Color, b: Color): Double {
        val (haut, bas) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (haut + 0.05) / (bas + 0.05)
    }
}
