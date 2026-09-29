package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Identifiant

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

/**
 * Le refus commun d'un statut HTTP, pour les verbes qui n'ont pas de phrase à eux — la table d'iOS/macOS
 * (`AnnuaireReel.refus`), à l'identique : un même `404` dit la même chose sur les deux plates-formes.
 */
internal fun refusDeStatut(statut: Int): ErreurAnnuaire = when (statut) {
    404 -> ErreurAnnuaire.Introuvable
    403 -> ErreurAnnuaire.Interdit
    409 -> ErreurAnnuaire.AliasPris
    // Un `401` arrive sur une connexion PROUVÉE : la demande est partie, et c'est l'annuaire qui ne tient plus
    // cet appareil pour vivant. « Rien n'a été envoyé » serait faux ici.
    401 -> ErreurAnnuaire.NonReconnu
    501 -> ErreurAnnuaire.NonImplemente
    else -> ErreurAnnuaire.RequeteInvalide("l'annuaire a répondu $statut")
}

/**
 * Le refus d'un rattachement, ou `null` pour un `204`. Ranger ([domaine] non nul) : un `404` dit que CE DOMAINE ne
 * reçoit pas la machine — le domaine racine, qui n'en reçoit aucune —, pas qu'elle a disparu. Retirer ([domaine]
 * nul) : un `404` garde le sens commun, la machine n'est pas (ou plus) à nous.
 */
internal fun refusDeRattachement(statut: Int, domaine: Identifiant?): ErreurAnnuaire? = when {
    statut == 204 -> null
    statut == 403 -> ErreurAnnuaire.SansDroitDeRattacher
    statut == 404 && domaine != null -> ErreurAnnuaire.DomaineNeRecoitPas
    else -> refusDeStatut(statut)
}

/**
 * `DELETE /v1/annuaires/{n}/membres/{n2}` — retirer le second membre d'une paire (`protocole.md` §2.2), comme le
 * Mac le fait depuis la 0.22.0 (`retirerMembre`).
 *
 * **Ce que l'annuaire répond, il n'y a que deux mots** : `204`, c'est fait ; `404` pour tout le reste — un membre
 * d'un autre annuaire, un annuaire qui n'est pas à moi (le propriétaire le peut, un administrateur des racines aussi),
 * un membre déjà retiré. C'est le `404` qui ne dit pas si l'objet existe, et l'écran le dit « Introuvable. », comme
 * le Mac. Nommer le titulaire à la place du second, c'est retirer la paire entière : l'écran ne le fait jamais par
 * ce chemin — il a son geste à lui, en rouge, en bas.
 *
 * Tenu à part de [AnnuaireReel], qui charge l'objet JNI : la requête et son verdict s'éprouvent sur la JVM.
 */
internal object RetraitDUnMembre {
    /** La méthode et le chemin. Les identifiants voyagent sous leur forme canonique. */
    fun requete(annuaire: Identifiant, membre: Identifiant): Pair<String, String> =
        "DELETE" to "/v1/annuaires/${annuaire.texte}/membres/${membre.texte}"

    /** `null` si c'est fait, le refus sinon. */
    fun verdict(statut: Int): ErreurAnnuaire? = if (statut == 204) null else refusDeStatut(statut)
}
