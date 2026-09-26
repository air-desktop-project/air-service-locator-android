package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.CodeInvitation
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.MachineVisible
import org.airdesktop.servicelocator.modele.Signataire

/** Ce que l'annuaire refuse, dans les termes de `docs/protocole.md` §2. */
sealed class ErreurAnnuaire(message: String) : Exception(message) {
    /** `404` — l'objet n'existe pas, OU il n'est pas à nous : c'est le même `404`, et c'est la propriété qui compte. */
    object Introuvable : ErreurAnnuaire("Introuvable.")
    /** `403` — un appareil se révoque lui-même. Le seul refus qui ne se cache pas. */
    object Interdit : ErreurAnnuaire("Un appareil ne peut pas se révoquer lui-même.")
    /** `409` — l'alias est pris. La demande est légitime, c'est l'état du monde qui s'y oppose. */
    object AliasPris : ErreurAnnuaire("Cet alias est déjà pris.")
    /** `400` — la faute est celle de l'appelant. */
    class RequeteInvalide(val champ: String) : ErreurAnnuaire("Demande refusée : $champ.")
    /** `501` — l'annuaire ne sait pas encore le dire (les expositions). */
    object NonImplemente : ErreurAnnuaire("L'annuaire ne sait pas encore le dire.")
    /**
     * Le code d'invitation n'a pas été accepté.
     *
     * **Une seule phrase pour trois causes**, et c'est l'annuaire qui le veut
     * ainsi (`protocole.md` §2.2) : un code faux, un code expiré et un code
     * déjà consommé rendent le même `403`, parce que distinguer « ce code
     * n'existe pas » de « ce code a servi » dirait à qui en essaie lesquels
     * ont existé. Trop d'essais depuis cette adresse (`429`) n'est PAS l'une
     * d'elles : c'est [TropDEssais], qui dit d'attendre et non de changer de code.
     */
    object InvitationRefusee : ErreurAnnuaire(
        "Ce code n'a pas été accepté : il est peut-être faux, déjà utilisé, ou expiré — demandez-en un autre à qui vous a invité."
    )
    /**
     * `429` — l'annuaire fait patienter après trop d'essais depuis cette
     * adresse. Ce n'est pas un refus : la même demande, dans une minute, peut
     * aboutir. Le dire à part évite d'envoyer chercher un autre code
     * quelqu'un qui n'avait qu'à attendre.
     */
    object TropDEssais : ErreurAnnuaire("Trop d'essais : l'annuaire fait patienter. Attendez une minute avant de réessayer.")
    /** Pas de réponse : l'annuaire injoignable, ou la connexion tombée. */
    class Reseau(detail: String) : ErreurAnnuaire("Annuaire injoignable : $detail")
    /** L'appareil n'a pas confirmé l'identité de son porteur ; la clé n'a pas signé, rien n'est parti. */
    object NonConfirme : ErreurAnnuaire("Identité non confirmée ; rien n'a été envoyé.")
    /** La preuve de possession ne vérifie pas sous la clé présentée. */
    object PreuveInvalide : ErreurAnnuaire("La preuve de possession de la clé ne vérifie pas.")
    /**
     * `401` — la clé qui signe n'est pas celle d'un appareil vivant : révoqué, ou d'un compte déjà effacé
     * (`protocole.md` §2.2). Ce n'est pas [NonConfirme] : le porteur a confirmé, la demande est partie, et c'est
     * l'annuaire qui ne reconnaît plus cet appareil.
     */
    object NonReconnu : ErreurAnnuaire("L'annuaire ne reconnaît plus cet appareil : révoqué, ou le compte est déjà effacé.")
    /**
     * Rejoindre a échoué APRÈS que l'autre téléphone a apporté la clé, et cette clé ne s'attestera plus jamais : son
     * défi vivait dans la connexion qui l'a tiré (`protocole.md` §2.2, « le défi vit ce que vit la connexion »).
     * La sortie est toujours la même — nouvelle clé, nouveau code — et l'appareil apporté reste dans le compte,
     * jusqu'à ce que l'autre téléphone le révoque.
     */
    class ARecommencer(detail: String) : ErreurAnnuaire(
        "$detail. Recommencez avec le nouveau code ci-dessus ; l'appareil que l'autre téléphone vient d'enrôler ne servira pas — révoquez-le depuis lui."
    )
}

/**
 * La voie des applications mobiles (`docs/protocole.md` §2), telle que les
 * écrans la voient.
 *
 * # Une interface, deux mises en œuvre
 *
 * Les écrans ne savent pas qui répond. Aujourd'hui c'est [AnnuaireSimule], en
 * mémoire, qui tient les mêmes règles que le serveur — c'est ce qui permet
 * d'écrire et d'éprouver les écrans avant que le transport soit embarqué.
 * Demain c'est la pile QUIC de `asl-client`, derrière son ABI C et JNI, et
 * **rien ici ne changera** : les écrans parlent à cette interface, pas au fil.
 *
 * **Toute requête est signée par la clé de l'appareil**, et la biométrie est
 * une condition d'usage de cette clé, appliquée par le matériel. Ce n'est pas
 * un paramètre : c'est ce qui se passe quand une méthode d'ici est appelée.
 */
/**
 * Sous quelle condition une racine laisse ouvrir un compte — ce que
 * `GET /v1/version` dit d'elle, sans que rien soit prouvé.
 *
 * **Ce n'est pas un secret, et c'est pourquoi l'annuaire le dit** : une racine
 * qui n'entre que sur invitation refuse toute création qui ne porte pas de
 * code, et quiconque essaie l'apprend en une requête. La cacher ne protégeait
 * rien — elle forçait seulement l'application à deviner, ou à échouer d'abord
 * pour comprendre ensuite.
 */
enum class Posture {
    /** L'annuaire exige une attestation de plate-forme. */
    Exigee,

    /** Il l'accepte sans l'exiger : un appareil nu entre. */
    Facultative,

    /** Il n'entre que sur un code émis par son exploitant (`protocole.md` §2.2). */
    Invitation,

    /**
     * L'annuaire ne l'a pas dite.
     *
     * **Un annuaire d'avant la 0.16.0 ne rend que sa version**, et une valeur
     * qu'on ne connaît pas viendra d'une version plus récente que celle-ci.
     * Les deux se traitent pareil : on ne suppose rien, et surtout pas
     * l'invitation — demander un code là où personne n'en donne serait une
     * porte fermée sur un annuaire ouvert.
     */
    NonDite,
    ;

    companion object {
        /** Ce que le fil en dit. Tout ce qui n'est pas reconnu est [NonDite], jamais une erreur. */
        fun depuisTexte(texte: String?): Posture = when (texte) {
            "required" -> Exigee
            "optional" -> Facultative
            "invitation" -> Invitation
            else -> NonDite
        }
    }
}

/** Ce qu'un annuaire dit de lui-même à qui n'a encore rien prouvé (`GET /v1/version`). */
data class Annonce(val version: String, val posture: Posture)

interface Annuaire {
    /**
     * `POST /v1/comptes` — crée le compte et enrôle cet appareil.
     *
     * **C'est l'annuaire qui conduit** : il tire le défi, connaît la liaison
     * de son canal, compose le message de possession et fait signer le
     * signataire — un seul geste biométrique, au moment exact où la preuve est
     * exigée. Le banc et le transport réel font la même chose, chacun avec ce
     * qu'il a.
     *
     * **[invitation] n'est donnée que sous la posture [Posture.Invitation]**,
     * et alors elle est exigée : le code part sous la plate-forme `3`, dans la
     * case où une chaîne d'attestation voyagerait, et l'annuaire le consomme
     * dans la transaction qui crée le compte. Sous les autres postures elle
     * est nulle, et la chaîne reprend sa place — sous `invitation`, seule la
     * plate-forme `3` entre (`protocole.md` §2.2).
     */
    suspend fun ouvrirCompte(signataire: Signataire, invitation: CodeInvitation? = null): Compte
    /**
     * La clé à montrer à l'autre téléphone pour rejoindre son compte —
     * **depuis le nouveau téléphone**, avant tout le reste.
     *
     * **L'ordre ne se négocie pas** (`protocole.md` §2.2, « Attester un
     * appareil qui rejoint ») : le transport réel se connecte nu, tire un
     * défi, et GÉNÈRE la clé avec le condensat du message d'attestation — c'est
     * ce qui rend sa chaîne présentable à la preuve. La connexion est tenue
     * jusqu'à [rejoindre] ; tant qu'elle tient, rappeler ceci rend la même clé
     * (un écran qui se redessine ne recommence pas). Tombée, ou après un
     * [rejoindre] qui a échoué, c'est une **nouvelle clé** — l'ancienne est
     * détruite, son défi est mort avec le canal. Le banc, lui, rend la clé du
     * signataire, et c'est tout. Aucun geste n'est demandé ici.
     */
    suspend fun clePourRejoindre(signataire: () -> Signataire): ByteArray
    /**
     * Rejoint un compte existant, **depuis le nouveau téléphone** : un appareil
     * déjà enrôlé a posté la clé montrée ([enrolerAppareil]) et lui a rendu
     * l'invitation. La clé est **prouvée**, sur la connexion tenue depuis
     * [clePourRejoindre], et sa chaîne d'attestation présentée du même geste
     * (`POST /v1/attestation`) : c'est ici que le porteur est sollicité. Ce qui
     * échoue après l'apport — chaîne refusée, connexion tombée, porteur qui n'a
     * pas confirmé, preuve refusée — rend [ErreurAnnuaire.ARecommencer] : la
     * clé ne s'attestera plus, on repart avec une neuve.
     */
    suspend fun rejoindre(compte: Identifiant, appareil: Identifiant, signataire: Signataire): Compte
    /** Quitte l'écran « rejoindre » sans avoir rejoint : la clé montrée ne servira pas, elle est détruite. */
    suspend fun annulerRejoindre()
    /** Le compte de cet appareil, s'il en a un. */
    suspend fun compte(): Compte?
    /**
     * `DELETE /v1/compte` — efface le compte, depuis cet appareil, et c'est le
     * dernier acte de sa clé (`protocole.md` §2.2, `modele.md` §2.1).
     *
     * Une transaction chez l'annuaire : tous les appareils révoqués — celui-ci
     * compris —, les machines et leurs services effacés, les autorisations
     * retirées dans les deux sens, l'alias libéré. Puis l'annuaire **ferme la
     * connexion** qui a porté la demande : c'est l'ordre attendu, pas une
     * panne. Au retour, [compte] rend `null` et ce que l'annuaire retenait
     * localement de ce compte est parti ; la clé de l'appareil, elle, est à
     * détruire par l'appelant — l'annuaire ne la tient pas.
     *
     * [ErreurAnnuaire.NonReconnu] si la clé n'est plus celle d'un appareil
     * vivant : révoqué, ou compte déjà effacé — l'effacement est idempotent
     * par construction, et un second appel le dit ainsi.
     */
    suspend fun effacerCompte()

    suspend fun machines(): List<Machine>
    /** `POST /v1/machines` — déclare, et rend la machine avec son code d'enrôlement. Elle n'a pas encore de clé. */
    suspend fun declarerMachine(nom: String, capacites: Set<Capacite>): Machine
    /** `PATCH /v1/machines/{m}` — ce qui est `null` ne change pas. */
    suspend fun modifierMachine(id: Identifiant, nom: String? = null, capacites: Set<Capacite>? = null): Machine
    /** `POST /v1/machines/{m}/enrolement` — le code précédent meurt à l'émission. */
    suspend fun emettreCode(machine: Identifiant): CodeEnrolement
    /** `DELETE /v1/machines/{m}/cle` — effet immédiat : connexions fermées, baux tombés. La machine reste. */
    suspend fun revoquerCle(machine: Identifiant)

    suspend fun appareils(): List<Appareil>
    /**
     * `POST /v1/appareils` — enrôle un appareil de plus, **depuis celui-ci** :
     * la clé que le nouveau téléphone a montrée. Rend l'appareil, dont
     * l'identifiant à lui rendre.
     */
    suspend fun enrolerAppareil(cle: ByteArray): Appareil
    /** `DELETE /v1/appareils/{a}` — marqué, non effacé. Jamais soi-même. */
    suspend fun revoquerAppareil(id: Identifiant)
    /**
     * `PUT /v1/appareils/{moi}/description` — ce que CET appareil est. **Pour soi seulement**, comme le jeton de
     * poussée ; posé juste après la preuve, reposé quand il change.
     */
    suspend fun decrire(description: Appareil.Description)
    /**
     * `PUT /v1/appareils/{moi}/poussee` — le point que le distributeur
     * UnifiedPush a donné à cet appareil (`protocole.md` §2.2). **Pour soi
     * seulement**, comme la description ; un seul point par appareil, le neuf
     * remplace l'ancien.
     *
     * **Il n'y a pas de verbe de retrait**, et ce n'est pas un oubli : couper
     * les notifications, c'est se désinscrire auprès du distributeur ; le
     * point meurt, et l'annuaire l'apprend au premier envoi. Le point part
     * aussi avec l'appareil qu'on révoque.
     *
     * [ErreurAnnuaire.RequeteInvalide] si l'annuaire refuse la forme du point.
     */
    suspend fun deposerPoint(point: String)

    /** `GET /v1/autorisations` — les deux sens, révoquées comprises. */
    suspend fun autorisations(): List<Autorisation>
    /**
     * `GET /v1/utilisateurs/{u}/machines` — les machines de `u` que ses autorisations envers moi donnent à voir ;
     * les miennes si `u` est moi ; vide sans aucune arête — vide, pas une erreur.
     */
    suspend fun machinesDe(utilisateur: Identifiant): List<MachineVisible>
    /**
     * `GET /v1/version` — ce que l'annuaire dit de lui-même, sans rien prouver :
     * sa version, et la posture sous laquelle il laisse ouvrir un compte.
     *
     * `null` si l'annuaire est trop ancien pour dire même sa version (`404`).
     * S'il la dit sans dire sa posture, celle-ci est [Posture.NonDite].
     */
    suspend fun annonce(): Annonce?
    /** `GET /v1/utilisateurs/{u}` — confirme qu'un identifiant existe, et rien d'autre. */
    suspend fun utilisateurExiste(id: Identifiant): Boolean
    /** `GET /v1/alias/{alias}` — rend l'identifiant, et rien d'autre. */
    suspend fun identifiantPourAlias(alias: String): Identifiant?
    /** `POST /v1/autorisations` — accorde, et notifie le bénéficiaire. */
    suspend fun accorder(beneficiaire: Identifiant, portee: Autorisation.Portee, etiquette: String): Autorisation
    /** `DELETE /v1/autorisations/{g}` — effet immédiat. */
    suspend fun revoquerAutorisation(id: Identifiant)

    /** `PUT /v1/alias`, `DELETE /v1/alias` avec `null`. */
    suspend fun definirAlias(alias: String?)

    /**
     * Rend ce que cet annuaire tient — sa connexion, son handle natif — parce
     * qu'on en choisit un autre ([ChoixDAnnuaire]).
     *
     * **Rien du compte n'est touché** : ni la clé de l'appareil, ni le carnet
     * local. Le compte existe sur chaque racine (elles se répliquent) ; seule
     * la connexion change. Plus rien ne doit être demandé à cet annuaire
     * ensuite.
     */
    suspend fun fermer()
}
