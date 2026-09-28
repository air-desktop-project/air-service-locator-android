package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.Machine

/**
 * Ce que chaque feuille de saisie enverrait — ou `null` : « Enregistrer » reste éteint.
 *
 * # LE BOUTON SE GRISE AVANT LE GESTE
 *
 * La règle est celle des feuilles du Mac (`actionPermise`) : une saisie que l'annuaire refuserait, ou qui ne change
 * rien, n'allume pas l'action. Ces fonctions ne décident rien de plus que l'annuaire ([Alias], [Machine.nomDHote]) ;
 * elles le disent d'avance, et se jugent sur la JVM sans écran.
 */
internal object Saisies {
    /** Créer un domaine : sans alias — il est facultatif —, ou avec un alias que l'annuaire rangerait. */
    fun creationDeDomaine(alias: String): Boolean = alias.isEmpty() || Alias.pourDomaine(alias) != null

    /** L'alias de domaine à ranger : valide, et autre que l'actuel une fois normalisé. Le retirer est un autre geste. */
    fun aliasDeDomaine(saisie: String, actuel: String?): String? = Alias.pourDomaine(saisie)?.takeIf { it != actuel }

    /** L'alias de machine à ranger : valide, et autre que l'actuel une fois normalisé. */
    fun aliasDeMachine(saisie: String, actuel: String?): String? = Alias.pourMachine(saisie)?.takeIf { it != actuel }

    /**
     * Le nom d'hôte à envoyer, tel que saisi — l'annuaire le range en minuscules, et la feuille dit sous quelle forme.
     * Comparé au nom tel qu'il est, comme le Mac : « Grenier » devant « grenier » s'envoie, et ne change rien.
     */
    fun nomDeMachine(saisie: String, actuel: String): String? = saisie.takeIf { Machine.nomValide(it) && it != actuel }

    /** Les capacités à envoyer, si elles changent. Aucune est un choix permis : la machine ne fait plus rien. */
    fun capacites(choisies: Set<Capacite>, actuelles: Set<Capacite>): Set<Capacite>? = choisies.takeIf { it != actuelles }

    /** Ce que la feuille de l'alias public ferait : le poser, ou — le champ vidé — le retirer. */
    sealed interface AliasDeCompte {
        data class Poser(val alias: String) : AliasDeCompte
        data object Retirer : AliasDeCompte
    }

    /**
     * L'alias public, comme la feuille du Mac : un champ vide retire l'alias qu'il y avait ; sinon un alias valide et
     * nouveau se pose. Vider un champ qui l'était déjà ne fait rien.
     */
    fun aliasDeCompte(saisie: String, actuel: String?): AliasDeCompte? =
        if (saisie.isEmpty()) AliasDeCompte.Retirer.takeIf { actuel != null }
        else Alias.pourCompte(saisie)?.takeIf { it != actuel }?.let { AliasDeCompte.Poser(it) }
}
