package org.airdesktop.servicelocator.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.airdesktop.servicelocator.modele.Echo
import java.time.Instant

/**
 * Ce que l'application dit de l'état d'écho d'une machine (serveur 0.43.0) — **à la lettre les mots de l'application
 * iOS/macOS**. Un texte qui change change des deux côtés.
 *
 * # LE LIBELLÉ DIT CE QUI EST PROUVÉ, LE DÉTAIL DIT COMMENT
 *
 * Un écho vérifié de l'intérieur ne vaut pas un écho vérifié du dehors : la clé répond, mais rien ne dit qu'on joint la
 * machine depuis l'Internet. Le libellé le dit dès le premier mot, la couleur aussi (l'accent, pas le vert), et le
 * détail l'explique. Une réponse signée par une autre clé est la seule chose rouge : l'adresse de la machine répond
 * pour quelqu'un d'autre.
 */
internal object TextesEcho {
    const val titre = "Écho"
    const val verifieDuDehors = "Écho vérifié, du dehors"
    const val verifieDeLInterieur = "Écho vérifié, de l'intérieur"
    const val verifie = "Écho vérifié"
    const val injoignable = "Écho injoignable"
    const val autreCle = "Écho signé par une autre clé"
    const val enCours = "Écho en cours de vérification"
    fun inconnu(mot: String) = "Écho : $mot"
    const val absent = "Pas d'écho"
    const val commentLeLancer = "Sur la machine : asl echo"

    const val viaUpnp = "par la redirection que la box a accordée (UPnP)"
    const val viaNat = "par le NAT que la connexion à l'annuaire tient ouvert"
    const val viaDirect = "en direct, sans traduction d'adresse"
    fun viaInconnu(mot: String) = "par « $mot »"
    const val detailAutreCle = "une réponse est venue, signée par une autre clé que celle de cette machine"
    const val detailInterieur = "prouvé de son réseau : sa clé répond, ce qui ne dit pas qu'on la joint du dehors"

    /** Le libellé ; `null` (l'annuaire n'en dit rien) se dit « Pas d'écho ». */
    fun libelle(echo: Echo?): String = when (echo?.etat) {
        null -> absent
        Echo.Etat.Verifie -> when (echo.depuis) {
            Echo.Depuis.Exterieur -> verifieDuDehors
            Echo.Depuis.Interieur -> verifieDeLInterieur
            null -> verifie
        }
        Echo.Etat.Injoignable -> injoignable
        Echo.Etat.AutreCle -> autreCle
        Echo.Etat.EnCours -> enCours
        Echo.Etat.Inconnu -> inconnu(echo.mot)
    }

    /**
     * Le détail, morceaux séparés par « , » dans cet ordre : par où (vérifié seulement), la phrase de l'autre clé puis
     * celle de l'intérieur, quand, par quel annuaire. `null` quand il n'y a rien à dire.
     */
    fun detail(echo: Echo, maintenant: Instant = Instant.now()): String? {
        val morceaux = buildList {
            if (echo.etat == Echo.Etat.Verifie) echo.via?.let { add(via(it)) }
            if (echo.etat == Echo.Etat.AutreCle) add(detailAutreCle)
            if (echo.etat == Echo.Etat.Verifie && echo.depuis == Echo.Depuis.Interieur) add(detailInterieur)
            echo.a?.let { add("constaté ${Formats.relatif(it, maintenant)}") }
            echo.par?.let { add("par l'annuaire ${it.abrege}") }
        }
        return morceaux.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun via(mot: String): String = when (mot) {
        "upnp" -> viaUpnp
        "nat" -> viaNat
        "direct" -> viaDirect
        else -> viaInconnu(mot)
    }

    /** Vert du dehors ; l'accent de l'intérieur (ou d'où, on ne sait) ; orange injoignable ; rouge l'autre clé ; gris le reste. */
    fun couleur(echo: Echo?): Color = when (echo?.etat) {
        Echo.Etat.Verifie -> if (echo.depuis == Echo.Depuis.Exterieur) Couleurs.joignable else Couleurs.accent
        Echo.Etat.Injoignable -> Couleurs.attention
        Echo.Etat.AutreCle -> Couleurs.erreur
        Echo.Etat.EnCours, Echo.Etat.Inconnu, null -> Couleurs.parti
    }
}

/** La ligne « Écho » de la fiche d'une machine : la pastille et le libellé, le détail dessous ; absent, comment le lancer. */
@Composable
fun LigneEcho(echo: Echo?) {
    LigneAGeste(TextesEcho.titre) {
        EtatDEcho(echo, MaterialTheme.typography.bodyMedium, 8)
        val sous = if (echo == null) TextesEcho.commentLeLancer else TextesEcho.detail(echo)
        sous?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * La forme compacte, sous une machine rangée dans un domaine. **L'absence ne s'y affiche pas** : dans une liste, un
 * « Pas d'écho » par machine serait du bruit, et le détail de la machine le dit.
 */
@Composable
fun EchoCompact(echo: Echo?) {
    if (echo == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        EtatDEcho(echo, MaterialTheme.typography.bodySmall, 6)
        TextesEcho.detail(echo)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun EtatDEcho(echo: Echo?, style: androidx.compose.ui.text.TextStyle, pastille: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pastille(TextesEcho.couleur(echo), pastille)
        Text(TextesEcho.libelle(echo), style = style)
    }
}
