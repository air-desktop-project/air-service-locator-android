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

/** Un téléphone enrôlé. **C'est l'appareil qui signe**, jamais l'utilisateur. */
data class Appareil(
    val id: Identifiant,
    /** Un nom d'affichage, tenu par l'appareil lui-même — l'annuaire ne le connaît pas. */
    val nom: String,
    val biometrie: Biometrie,
    val enroleLe: Instant,
    /**
     * Un appareil révoqué reste dans la liste, marqué : l'écran qu'on regarde
     * après avoir perdu un téléphone doit montrer ce qu'on a retiré.
     */
    val revoqueLe: Instant? = null,
    /** Celui qui affiche l'écran. Il ne peut pas se révoquer lui-même. */
    val estCeluiCi: Boolean = false,
) {
    enum class Biometrie { VISAGE, EMPREINTE }

    val estRevoque: Boolean get() = revoqueLe != null
}
