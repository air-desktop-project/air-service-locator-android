package org.airdesktop.servicelocator.reseau

/**
 * Où en sont les notifications de cet appareil, dit en une valeur — ce que
 * l'écran Compte montre.
 *
 * # SANS DISTRIBUTEUR, RIEN NE CASSE
 *
 * UnifiedPush ne fournit pas de service : c'est l'utilisateur qui installe un
 * distributeur (ntfy, NextPush, …) et le choisit. Sans lui, l'application ne
 * reçoit rien — et c'est un état normal, pas une panne : la relecture à
 * l'ouverture ([Nouveautes]) montre ce qui a été accordé. L'écran le dit
 * sobrement, et dit comment en avoir un, sans en imposer aucun.
 *
 * Décidé ici, en Kotlin pur, plutôt que dans l'écran : c'est ce qui permet de
 * l'éprouver sans téléphone.
 */
sealed interface EtatNotifications {
    /** Aucun distributeur installé — ou celui qu'on avait choisi a été désinstallé. */
    data object SansDistributeur : EtatNotifications

    /** Des distributeurs sont là, aucun n'est choisi : c'est à l'utilisateur de le faire. */
    data class AChoisir(val distributeurs: List<String>) : EtatNotifications

    /** Choisi, et le distributeur n'a pas encore rendu de point. */
    data class EnAttente(val distributeur: String) : EtatNotifications

    /**
     * Choisi, un point rendu. [depose] dit si l'annuaire l'a ; [refus] ce qu'il a répondu s'il l'a refusé — un
     * distributeur auto-hébergé hors de `https://` sur 443, typiquement.
     */
    data class Actif(val distributeur: String, val depose: Boolean, val refus: String?) : EtatNotifications

    companion object {
        /**
         * @param installes les paquets qui se déclarent distributeurs UnifiedPush.
         * @param retenu celui que cet appareil a choisi et que le distributeur a accepté, s'il y en a un.
         * @param point le dernier point rendu par ce distributeur.
         * @param depose le point que l'annuaire a accepté pour ce compte.
         */
        fun de(installes: List<String>, retenu: String?, point: String?, depose: String?, refus: String?): EtatNotifications = when {
            retenu != null && retenu in installes ->
                if (point == null) EnAttente(retenu) else Actif(retenu, depose == point, refus.takeIf { depose != point })
            installes.isEmpty() -> SansDistributeur
            else -> AChoisir(installes)
        }
    }
}
