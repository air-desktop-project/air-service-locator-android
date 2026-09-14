package org.airdesktop.servicelocator.composants

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'application parle français ; ses dates aussi, quel que soit le réglage du
 * téléphone — un « il y a 2 min » au milieu d'un écran français ne doit pas
 * devenir « 2 minutes ago » parce que l'appareil est en anglais.
 */
object Formats {
    private val jourFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

    /** « il y a 2 min », « hier », « il y a 3 j ». */
    fun relatif(instant: Instant, maintenant: Instant = Instant.now()): String {
        val d = Duration.between(instant, maintenant)
        val s = d.seconds
        return when {
            s < 60 -> "à l'instant"
            s < 3_600 -> "il y a ${s / 60} min"
            s < 86_400 -> "il y a ${s / 3_600} h"
            s < 2 * 86_400 -> "hier"
            s < 30 * 86_400 -> "il y a ${s / 86_400} j"
            else -> "le ${jour(instant)}"
        }
    }

    /** « 11 sept. 2026 ». */
    fun jour(instant: Instant): String = jourFormat.format(instant.atZone(ZoneId.systemDefault()))

    /** « 9:40 » — ce qu'il reste à un code. */
    fun minutesSecondes(duree: Duration): String = "%d:%02d".format(duree.toMinutes(), duree.seconds % 60)
}
