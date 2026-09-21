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
    /** Absente tant que l'appareil ne l'a pas posée ; reste sur un appareil révoqué. */
    val description: Description? = null,
) {
    enum class Biometrie { VISAGE, EMPREINTE }
    /**
     * Sous quoi l'appareil est entré (`docs/modele.md` §2.2) : `android` est l'attestation de clé du Keystore, vérifiée
     * hors ligne (C19) ; `invitation`, un code de l'exploitant ; `attendue`, une clé qu'un autre appareil du compte a
     * apportée sous une posture exigée et que son porteur n'a pas encore prouvée ni attestée — il n'est pas entré.
     */
    enum class Attestation(val libelle: String) { AUCUNE("aucune"), APPLE("apple"), ANDROID("android"), INVITATION("invitation"), ATTENDUE("attendue") }

    /** Ce que l'appareil fait tourner : une liste fermée, celle des applications de ce produit (`docs/protocole.md` §2.2). */
    enum class Plateforme(val libelle: String) { IOS("ios"), ANDROID("android"), MACOS("macos") }

    /**
     * Ce que l'appareil dit de lui-même (`docs/modele.md` §2.2) : sa plate-forme et son **modèle** —
     * « Fairphone FP5 », jamais le nom que l'utilisateur a donné au téléphone, qui porte souvent un prénom (C13).
     *
     * **Une étiquette, pas une preuve.** L'annuaire ne vérifie rien de ce qu'elle dit ; un appareil pirate peut se
     * dire « iPhone 17 ». Ce qui identifie un appareil est son `a-…`, affiché à côté. L'étiquette sert à ce que
     * l'écran Compte montre « MacBook Pro » plutôt que « Autre » — de quoi reconnaître les siens, pas de quoi les prouver.
     */
    data class Description(val plateforme: Plateforme, val modele: String) {
        companion object {
            /** Le modèle, 1 à 64 octets : les règles du nom de machine. */
            const val MODELE_OCTETS_MAX = 64
        }
    }

    val estRevoque: Boolean get() = revoque || revoqueLe != null

    /** Ce que l'écran affiche en titre : le modèle que l'annuaire rend, sinon le nom de repli. */
    val titre: String get() = description?.modele ?: nom

    fun revoque(oui: Boolean) = copy(revoque = oui)
}
