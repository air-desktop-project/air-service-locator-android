package org.airdesktop.servicelocator.notifications

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.airdesktop.servicelocator.modele.Identifiant

/**
 * Ce que cet appareil retient des notifications — et rien de ce qu'elles
 * disent, puisqu'elles ne disent rien.
 *
 * **Hors du `Carnet` de l'annuaire réel**, et pour deux raisons. Le banc en
 * mémoire en a besoin aussi : la relecture avec différence marche sans
 * annuaire. Et c'est le récepteur UnifiedPush qui écrit ici, parfois dans un
 * processus que le distributeur vient de réveiller et où aucun annuaire n'a
 * été construit — ce qui chargerait le transport natif pour rien.
 *
 * **Le déposé et le vu se rangent sous le compte.** Un téléphone qui efface
 * son compte et en ouvre un autre, ou qui en rejoint un, est un nouvel
 * appareil pour l'annuaire : le point est à redéposer, et ce qu'il a vu de
 * l'ancien compte ne dit rien du nouveau.
 */
class CarnetNotifications(contexte: Context) {
    private val prefs = contexte.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    /** Monte à chaque écriture : un écran qui le lit se redessine quand le récepteur écrit, sans l'interroger. */
    var tour by mutableIntStateOf(0)
        private set

    private fun ecrire(bloc: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(bloc).apply()
        tour++
    }

    /** Le dernier point que le distributeur a donné ; `null` tant qu'il n'en a pas donné, ou depuis qu'il l'a retiré. */
    var point: String?
        get() = prefs.getString("point", null)
        set(valeur) = ecrire { putString("point", valeur) }

    /** Le point que l'annuaire a accepté pour ce compte. */
    fun depose(compte: Identifiant): String? =
        prefs.getString("depose", null)?.takeIf { it.startsWith(compte.texte + " ") }?.substringAfter(' ')

    fun retenirDepose(compte: Identifiant, point: String) = ecrire { putString("depose", "${compte.texte} $point"); remove("refus") }

    /** La règle que l'annuaire a opposée au dernier point, dite à l'écran jusqu'au prochain dépôt réussi. */
    var refus: String?
        get() = prefs.getString("refus", null)
        set(valeur) = ecrire { putString("refus", valeur) }

    /**
     * Le distributeur a retiré l'inscription, ou l'utilisateur l'a coupée : le point est mort. Le déposé part avec
     * lui — un point redonné identique plus tard doit repartir, l'annuaire a pu l'oublier au premier envoi refusé.
     */
    fun oublierPoint() = ecrire { remove("point"); remove("depose"); remove("refus") }

    /**
     * Une notification est arrivée depuis la dernière ouverture : la prochaine
     * relit les autorisations, même si l'écran était resté ouvert derrière.
     */
    var aRelire: Boolean
        get() = prefs.getBoolean("a_relire", false)
        set(valeur) = ecrire { putBoolean("a_relire", valeur) }

    /** Les `g-…` reçues déjà montrées pour ce compte ; `null` si ce téléphone n'en a jamais tenu la liste. */
    fun dejaVues(compte: Identifiant): Set<Identifiant>? {
        if (prefs.getString("vues_compte", null) != compte.texte) return null
        return prefs.getStringSet("vues", emptySet())!!.mapNotNull { runCatching { Identifiant.analyser(it) }.getOrNull() }.toSet()
    }

    fun retenirVues(compte: Identifiant, vues: Set<Identifiant>) =
        ecrire { putString("vues_compte", compte.texte); putStringSet("vues", vues.map { it.texte }.toSet()) }
}
