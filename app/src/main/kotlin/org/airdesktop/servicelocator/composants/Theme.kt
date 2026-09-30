package org.airdesktop.servicelocator.composants

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Les couleurs décidées du produit, partagées avec iOS. Le reste est celui de Material 3.
 *
 * ## Les trois états ont les couleurs des boutons de fenêtre de macOS
 *
 * Rouge, orange et vert sont ceux que macOS donne à fermer, réduire et plein écran (`#FF5F57`, `#FEBC2E`, `#28C840`) :
 * l'utilisateur les a sous les yeux toute la journée, en haut de chaque fenêtre, et les lit sans y penser. Le vert
 * d'avant (`#2E7D32`) était trop sombre pour une pastille de huit points — il passait pour du gris.
 *
 * ## Une pastille et un texte ne se teignent pas pareil
 *
 * Ces trois couleurs sont faites pour des DISQUES PLEINS. Écrit en `#FEBC2E` sur fond blanc, un mot ne se lit plus.
 * [Texte] porte donc les mêmes teintes, assombries pour le fond clair — et les vives pour le fond sombre, où ce sont
 * elles qui se lisent. Pastilles et fonds de badge prennent la couleur d'ici ; tout ce qui s'écrit — libellé, badge,
 * icône, message d'erreur — prend celle de [Texte].
 */
object Couleurs {
    val accent = Color(0xFF2D6BB0)

    /** Le vert de « plein écran » : joignable, vivant, actif. */
    val joignable = Color(0xFF28C840)

    /** L'orange de « réduire » : injoignable, en attente, à surveiller. */
    val attention = Color(0xFFFEBC2E)

    /** Le rouge de « fermer » : refusé, révoqué, une erreur. */
    val alerte = Color(0xFFFF5F57)

    /** Ni bon ni mauvais : parti, pas encore posé, rien à dire. */
    val parti = Color(0xFF73777F)

    /** Un point d'écoute UDP, que l'annuaire ne sonde pas : plus pâle encore que « parti » — il n'y a rien à conclure. */
    val nonSonde = Color(0xFFC3C6CF)

    /** Le fond d'un encart d'avertissement. C'est un fond et non une teinte d'état : il ne se lit pas, il se remarque. */
    val attentionFond = Color(0xFFFFF3E0)

    /**
     * Les mêmes trois teintes, pour ce qui s'écrit : assombries sur fond clair, vives sur fond sombre.
     *
     * Le fond regardé est **celui du thème appliqué**, et non le réglage du téléphone : l'application peint une seule
     * palette, claire, quoi que dise le système ; l'encre doit suivre le fond réellement peint. Le jour où une palette
     * sombre arrive, ce sont les teintes vives qui sortiront d'ici, sans qu'on y retouche.
     */
    object Texte {
        /** Le vert de « vivant », « joignable », « acceptée » — tel qu'on l'écrit. */
        val joignable: Color @Composable get() = encre(Couleurs.joignable)

        /** L'orange de « injoignable », « en attente », « voie tombée » — tel qu'on l'écrit. */
        val attention: Color @Composable get() = encre(Couleurs.attention)

        /** Le rouge de « refusée », « autre clé », d'un message d'erreur — tel qu'on l'écrit. */
        val alerte: Color @Composable get() = encre(Couleurs.alerte)

        /**
         * L'encre d'une teinte d'état : la même couleur, mais celle qui se lit sur le fond du thème. **Tout ce qui
         * écrit un mot ou trace une icône passe par ici** — c'est le seul endroit d'où une teinte vive pourrait
         * atterrir dans du texte, donc le seul à vérifier.
         */
        @Composable
        fun encre(vif: Color): Color = encre(vif, fondSombre = MaterialTheme.colorScheme.surface.luminance() < 0.5f)

        /** La même table, hors composition : ce que les essais lisent. */
        fun encre(vif: Color, fondSombre: Boolean): Color = if (fondSombre) vif else surClair(vif)

        /**
         * Les trois vifs, assombris jusqu'à se lire sur du blanc. Une couleur qui n'est pas un état — l'accent, le gris
         * de « parti », une teinte du thème — se rend telle quelle : elle se lisait déjà.
         */
        fun surClair(vif: Color): Color = when (vif) {
            Couleurs.joignable -> Color(0xFF1C8C2C)
            Couleurs.attention -> Color(0xFF9A6900)
            Couleurs.alerte -> Color(0xFFC42B24)
            else -> vif
        }
    }
}

private val palette = lightColorScheme(
    primary = Couleurs.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FA),
    onPrimaryContainer = Color(0xFF001C39),
    secondaryContainer = Color(0xFFDCE2EF),
    onSecondaryContainer = Color(0xFF1A1C1E),
    // `Scaffold` peint `background`, les listes `surface` : les deux doivent être la même teinte.
    background = Color(0xFFF9F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF9F9FC),
    surfaceContainerLow = Color(0xFFF3F4F8),
    surfaceContainer = Color(0xFFEDEEF2),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    // Cette palette est claire, et `error` ne sert qu'à écrire ou à border : c'est l'encre du rouge, pas son vif — les
    // champs en faute de Material 3 doivent se lire comme le reste.
    error = Couleurs.Texte.surClair(Couleurs.alerte),
)

@Composable
fun ThemeServiceLocator(contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, content = contenu)
}
