package org.airdesktop.servicelocator.composants

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Les icônes de l'application, tracées au trait sur une grille de 24 — les
 * mêmes tracés que les maquettes. Dessinées ici plutôt que tirées de
 * `material-icons-extended`, qui pèse plusieurs mégaoctets pour une douzaine
 * de glyphes.
 */
object Icones {
    private fun trait(nom: String, vararg chemins: String, epaisseur: Float = 1.8f): ImageVector =
        ImageVector.Builder(name = nom, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in chemins) addPath(
                    pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = epaisseur,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()

    val machine = trait("machine", "M3 4h18v12H3z", "M8 20h8M12 16v4")
    val acces = trait("acces", "M8 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z", "M11.5 12H21M18 12v3M15 12v2")
    val compte = trait("compte", "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M4 21c0-4 3.6-7 8-7s8 3 8 7")
    val chevron = trait("chevron", "M9 6l6 6-6 6")
    val retour = trait("retour", "M15 6l-6 6 6 6", epaisseur = 2f)
    val plus = trait("plus", "M12 5v14M5 12h14", epaisseur = 2f)
    val copier = trait("copier", "M9 9h11v11H9z", "M5 15V5a1 1 0 0 1 1-1h10")
    val partager = trait("partager", "M12 3v12M7 8l5-5 5 5M5 14v6h14v-6")
    val visage = trait("visage", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M9 9.5v1M15 9.5v1M12 9v4h-1M9 15.5c1.5 1.3 4.5 1.3 6 0")
    val empreinte = trait("empreinte", "M7 19c1.5-2.5 2-5 2-8a3 3 0 0 1 6 0c0 3 .5 6 2 8M4.5 16c1-2.5 1.5-5 1.5-7a6 6 0 0 1 12 0c0 2 .3 4 .9 6M12 11c0 3.5-.7 6.5-2 9")
    val cle = trait("cle", "M8 11a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M11 12l9-9M17 6l2 2M14 9l2 2")
    val alerte = trait("alerte", "M12 4l9 16H3z", "M12 10v4M12 17v.5")
    val horloge = trait("horloge", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 7v5l3 2")
    val coche = trait("coche", "M5 12l5 5 9-10", epaisseur = 2f)
    val croix = trait("croix", "M6 6l12 12M18 6L6 18", epaisseur = 2f)
    val terminal = trait("terminal", "M3 5h18v14H3z", "M7 9l3 3-3 3M12 15h5")
    val oeil = trait("oeil", "M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    val branche = trait("branche", "M6 3v12M6 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM18 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM18 9c0 4-4 6-12 6")
    val telephone = trait("telephone", "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z", "M11 18h2")
}
