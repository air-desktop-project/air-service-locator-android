package org.airdesktop.servicelocator.reseau

// Ce que deviennent les codes de refus de l'objet natif, en fonctions pures :
// l'annuaire réel ne se construit pas sur la JVM (il charge l'objet JNI), mais
// ces correspondances, si — et ce sont elles qui décident de la phrase que
// l'utilisateur lit. Les constantes de `Natif` sont des `const val`, inlinées à
// la compilation : les nommer ici ne charge pas la bibliothèque.

/**
 * Le refus d'une ouverture de compte sur invitation, ou `null` si le code n'en
 * est pas un et doit suivre le chemin commun. `403` et `429` ne disent pas la
 * même chose : un code refusé, ou une minute à attendre.
 */
internal fun refusDInvitation(code: Int): ErreurAnnuaire? = when (code) {
    Natif.REFUSE -> ErreurAnnuaire.InvitationRefusee
    Natif.TROP_D_ESSAIS -> ErreurAnnuaire.TropDEssais
    else -> null
}

/**
 * Le refus d'une preuve d'appareil qui rejoint, ou `null` pour un code que
 * l'application ne sait pas nommer. Tous sont « à recommencer » : le défi de
 * cette clé est dépensé dès que la preuve est partie, et la clé ne
 * s'attestera plus. Un `429` n'y change rien — l'annuaire ne le rend pas ici
 * aujourd'hui —, mais la phrase dit alors d'attendre avant de recommencer.
 */
internal fun refusDeRejoindre(code: Int): ErreurAnnuaire? = when (code) {
    // **UN REFUS D'EMPREINTE NE DÉPENSE RIEN** : le porteur a annulé avant
    // qu'un octet parte, et depuis le client 0.9.1 le défi reste sur la
    // connexion. La clé préparée vaut toujours : on redemande le geste, sans
    // nouvelle clé ni nouveau code — recommencer laisserait chez l'annuaire un
    // appareil apporté, à révoquer à la main.
    Natif.SIGNATURE_REFUSEE -> ErreurAnnuaire.NonConfirme
    Natif.CHAINE_REFUSEE -> ErreurAnnuaire.ARecommencer("L'annuaire exige une attestation et a refusé celle de cette clé")
    Natif.TROP_D_ESSAIS -> ErreurAnnuaire.ARecommencer("L'annuaire fait patienter après trop d'essais : attendez une minute")
    Natif.REFUSE -> ErreurAnnuaire.ARecommencer("L'annuaire a refusé la preuve de cette clé")
    Natif.INJOIGNABLE, Natif.NON_CONNECTE -> ErreurAnnuaire.ARecommencer("La connexion est tombée entre le code et la preuve")
    else -> null
}
