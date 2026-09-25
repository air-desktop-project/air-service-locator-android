package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Identifiant

/**
 * La relecture avec différence (`protocole.md` §2.2, « Le repli, partout ») :
 * ce qui a été accordé à ce compte depuis la dernière fois qu'on l'a montré.
 *
 * # POURQUOI CE N'EST PAS L'ANNUAIRE QUI LE DIT
 *
 * L'annuaire ne tient ni compteur ni date de lecture, et c'est voulu : une
 * « dernière lecture » rangée chez lui serait une donnée de plus sur ce que
 * fait l'utilisateur. C'est le téléphone qui garde l'ensemble des `g-…` reçues
 * qu'il a déjà montrées ; la différence avec `GET /v1/autorisations` est ce
 * qu'il y a de nouveau. C'est ce qui fait marcher l'application SANS
 * notification — sans distributeur, notification perdue, permission refusée :
 * l'ouverture fait le travail.
 *
 * # LA PREMIÈRE LECTURE POSE LA RÉFÉRENCE, ET NE SIGNALE RIEN
 *
 * Un ensemble jamais tenu (`null`) n'est pas un ensemble vide : c'est une
 * application qui vient d'être mise à jour, ou un téléphone qui vient de
 * rejoindre le compte. Tout ce qu'il lit alors, il le signalerait comme neuf —
 * des accès de six mois, sous « nouveau ». La première lecture ne signale
 * donc rien : elle dit ce qui est, et c'est à partir d'elle qu'on compare.
 */
object Nouveautes {
    /**
     * @param dejaVues les `g-…` reçues déjà montrées ; `null` si ce téléphone n'en a jamais tenu la liste.
     * @return [Lecture.nouvelles] à signaler, et [Lecture.aRetenir], l'ensemble à garder une fois qu'elles ont été montrées.
     */
    fun lire(autorisations: List<Autorisation>, moi: Identifiant, dejaVues: Set<Identifiant>?): Lecture {
        // Reçues et vivantes : un accès qu'on m'a retiré avant que je l'aie vu n'est pas une nouvelle à annoncer.
        val recues = autorisations.filter { it.accordeeA == moi && it.accordeePar != moi }
        val nouvelles = if (dejaVues == null) emptyList() else recues.filter { !it.estRevoquee && it.id !in dejaVues }
        // Toutes les reçues, révoquées comprises : un identifiant ne resert jamais, et une révoquée ne doit pas
        // revenir comme neuve. Ce que la liste ne rend plus n'a pas à rester : il ne reviendra pas.
        return Lecture(nouvelles, recues.map { it.id }.toSet())
    }

    data class Lecture(val nouvelles: List<Autorisation>, val aRetenir: Set<Identifiant>)
}
