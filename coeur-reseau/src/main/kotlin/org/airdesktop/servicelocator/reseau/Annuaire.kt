package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
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
    /** Pas de réponse : l'annuaire injoignable, ou la connexion tombée. */
    class Reseau(detail: String) : ErreurAnnuaire("Annuaire injoignable : $detail")
    /** L'appareil n'a pas confirmé l'identité de son porteur ; la clé n'a pas signé, rien n'est parti. */
    object NonConfirme : ErreurAnnuaire("Identité non confirmée ; rien n'a été envoyé.")
    /** La preuve de possession ne vérifie pas sous la clé présentée. */
    object PreuveInvalide : ErreurAnnuaire("La preuve de possession de la clé ne vérifie pas.")
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
interface Annuaire {
    /**
     * `POST /v1/comptes` — crée le compte et enrôle cet appareil.
     *
     * **C'est l'annuaire qui conduit** : il tire le défi, connaît la liaison
     * de son canal, compose le message de possession et fait signer le
     * signataire — un seul geste biométrique, au moment exact où la preuve est
     * exigée. Le banc et le transport réel font la même chose, chacun avec ce
     * qu'il a.
     */
    suspend fun ouvrirCompte(signataire: Signataire): Compte
    /** Le compte de cet appareil, s'il en a un. */
    suspend fun compte(): Compte?

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
    /** `DELETE /v1/appareils/{a}` — marqué, non effacé. Jamais soi-même. */
    suspend fun revoquerAppareil(id: Identifiant)

    /** `GET /v1/autorisations` — les deux sens, révoquées comprises. */
    suspend fun autorisations(): List<Autorisation>
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
}
