package org.airdesktop.servicelocator.composants

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** La seule teinte décidée du produit, partagée avec iOS. Le reste est celui de Material 3. */
object Couleurs {
    val accent = Color(0xFF2D6BB0)
    val joignable = Color(0xFF2E7D32)
    val attention = Color(0xFFC77700)
    val attentionFond = Color(0xFFFFF3E0)
    val parti = Color(0xFF73777F)
    val erreur = Color(0xFFC62828)
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
    error = Couleurs.erreur,
)

@Composable
fun ThemeServiceLocator(contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, content = contenu)
}
