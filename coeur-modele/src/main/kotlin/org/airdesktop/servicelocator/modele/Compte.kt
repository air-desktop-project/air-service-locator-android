package org.airdesktop.servicelocator.modele

import java.time.Instant

/**
 * Le compte : un identifiant, un jeu d'appareils, et rien d'autre.
 *
 * Pas de courriel, pas de numéro, pas de nom, pas de mot de passe. La seule
 * donnée confiée est l'**alias**, facultatif, public et devinable — il ne rend
 * que l'identifiant, et ne l'enregistrer jamais est une position tenable.
 */
data class Compte(val identifiant: Identifiant, val alias: String? = null)

/**
 * Un téléphone enrôlé. **C'est l'appareil qui signe**, jamais l'utilisateur.
 *
 * L'annuaire en rend l'identifiant, l'attestation sous laquelle il est entré
 * et s'il est révoqué — pas de date : il n'en range aucune. Les dates ne sont
 * connues que du téléphone qui a agi, et la biométrie que du téléphone
 * lui-même ; `null` dit « inconnu d'ici ».
 */
data class Appareil(
    val id: Identifiant,
    /** Un nom d'affichage, tenu par l'appareil lui-même — l'annuaire ne le connaît pas. */
    val nom: String,
    val biometrie: Biometrie? = null,
    val enroleLe: Instant? = null,
    /**
     * Un appareil révoqué reste dans la liste, marqué : l'écran qu'on regarde
     * après avoir perdu un téléphone doit montrer ce qu'on a retiré.
     */
    val revoqueLe: Instant? = null,
    /** Celui qui affiche l'écran. Il ne peut pas se révoquer lui-même. */
    val estCeluiCi: Boolean = false,
    /** Sous quoi l'appareil est entré (`docs/modele.md` §2.2) : une valeur, pas une absence. */
    val attestation: Attestation? = null,
    /** Révoqué sans que l'on sache quand : l'annuaire le dit, sans date. */
    private val revoque: Boolean = false,
) {
    enum class Biometrie { VISAGE, EMPREINTE }
    enum class Attestation(val libelle: String) { AUCUNE("aucune"), APPLE("apple"), GOOGLE("google") }

    val estRevoque: Boolean get() = revoque || revoqueLe != null

    fun revoque(oui: Boolean) = copy(revoque = oui)
}
