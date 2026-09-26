package org.airdesktop.servicelocator

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.identite.CleAppareil
import org.airdesktop.servicelocator.identite.deCetAppareil
import org.airdesktop.servicelocator.identite.IdentiteLocale
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.CodeInvitation
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Signataire
import org.airdesktop.servicelocator.notifications.CarnetNotifications
import org.airdesktop.servicelocator.reseau.Annuaire
import org.airdesktop.servicelocator.reseau.ChoixDAnnuaire
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire
import org.airdesktop.servicelocator.reseau.Nouveautes
import org.airdesktop.servicelocator.reseau.RacineDAnnuaire

/**
 * Ce que tous les écrans partagent : l'annuaire à qui parler, et le compte de
 * cet appareil.
 */
class Session(
    annuaire: Annuaire,
    val identite: IdentiteLocale,
    /** Le point de poussée et les accès déjà montrés — ce que cet appareil retient des notifications. */
    val notifications: CarnetNotifications,
    /** D'où vient la clé : le Keystore sur un appareil, une clé logicielle dans un essai. */
    private val signataire: (FragmentActivity) -> Signataire = { CleAppareil.ouOuvrir().avec(it) },
    /** Comment on détruit la clé de cet appareil, une fois qu'elle a servi pour la dernière fois. Le Keystore sur un appareil ; rien dans un essai. */
    private val effacerCle: () -> Unit = { CleAppareil.effacer() },
    /**
     * La portée de ce qui appartient à la session et non à un écran : elle vit
     * autant que l'application, là où celle d'un écran meurt avec lui.
     */
    private val portee: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    /**
     * Les racines entre lesquelles choisir, et celle qui sert — `null` sur le
     * banc en mémoire, qui n'en a qu'une. Voir [choisirAnnuaire].
     */
    val choix: ChoixDAnnuaire<*>? = null,
    /**
     * Comment on ouvre un compte — séparé de l'annuaire parce qu'en
     * démonstration, l'ouverture peuple aussi l'annuaire. La clé est donnée
     * PARESSEUSEMENT : l'annuaire réel la crée lui-même, avec le défi
     * d'attestation de sa connexion, et ne doit pas la trouver déjà faite.
     */
    private val ouverture: suspend (CodeInvitation?, () -> Signataire) -> Compte,
) {
    /**
     * L'annuaire à qui parler — celui de la racine choisie. **Un état**, et
     * non une constante : changer de racine le remplace, et les écrans qui le
     * lisent se recomposent sur le nouveau.
     */
    var annuaire: Annuaire by mutableStateOf(annuaire)
        private set

    var compte: Compte? by mutableStateOf(null)
        private set

    /**
     * Combien d'accès reçus n'ont pas encore été montrés — ce que l'onglet
     * « Accès » porte en pastille. Tenu par [relire], remis à zéro quand
     * l'écran des accès les a montrés.
     */
    var nouveautes by mutableIntStateOf(0)
        private set

    /** Relit le compte que l'annuaire connaît pour cet appareil. */
    suspend fun rafraichirCompte() {
        compte = runCatching { annuaire.compte() }.getOrNull()
        if (compte != null) {
            seDecrire()
            deposerPoint()
        }
    }

    /**
     * Dépose chez l'annuaire le point que le distributeur UnifiedPush a donné,
     * s'il ne l'a pas déjà pour ce compte. Comme la description, juste après
     * une preuve — c'est le seul moment où l'appareil parle sur sa propre
     * connexion ; un point arrivé pendant que l'application dormait part donc
     * à l'ouverture suivante.
     *
     * **Un refus de forme se retient et se dit** (Compte › Notifications) : un
     * distributeur auto-hébergé hors de `https://` sur 443 ne réveillera jamais
     * rien, et l'utilisateur doit l'apprendre. Une panne de réseau ne se retient
     * pas : le dépôt repartira.
     */
    fun deposerPoint() {
        portee.launch {
            val moi = compte?.identifiant ?: return@launch
            val point = notifications.point ?: return@launch
            if (notifications.depose(moi) == point) return@launch
            runCatching { annuaire.deposerPoint(point) }
                .onSuccess { notifications.retenirDepose(moi, point) }
                .onFailure {
                    Log.i("Session", "le point de poussée n'a pas été déposé : ${it.message}")
                    if (it is ErreurAnnuaire.RequeteInvalide) notifications.refus = it.champ
                }
        }
    }

    /**
     * La relecture à l'ouverture (`protocole.md` §2.2) : `GET /v1/autorisations`,
     * et la différence avec ce qui a déjà été montré. C'est elle, et non la
     * notification, qui dit ce qui a changé — la notification ne dit que qu'il
     * y a quelque chose à relire, et elle peut manquer.
     *
     * La première lecture sur ce téléphone pose la référence ([Nouveautes]) :
     * elle est retenue tout de suite, sans rien signaler.
     */
    suspend fun relire() {
        val moi = compte?.identifiant ?: return
        val autorisations = runCatching { annuaire.autorisations() }.getOrNull() ?: return
        notifications.aRelire = false
        val lecture = lire(autorisations)
        if (notifications.dejaVues(moi) == null) notifications.retenirVues(moi, lecture.aRetenir)
        nouveautes = lecture.nouvelles.size
    }

    /** La différence entre ces autorisations et ce que ce téléphone a déjà montré. */
    fun lire(autorisations: List<Autorisation>): Nouveautes.Lecture {
        val moi = compte?.identifiant ?: return Nouveautes.Lecture(emptyList(), emptySet())
        return Nouveautes.lire(autorisations, moi, notifications.dejaVues(moi))
    }

    /** L'écran des accès a montré cette lecture : ce qu'elle portait de neuf ne l'est plus. */
    fun montrees(lecture: Nouveautes.Lecture) {
        val moi = compte?.identifiant ?: return
        notifications.retenirVues(moi, lecture.aRetenir)
        nouveautes = 0
    }

    /**
     * Dit à l'annuaire ce que cet appareil est — plate-forme et modèle, jamais
     * le nom que l'utilisateur lui a donné (`docs/modele.md` §2.2). Juste après
     * chaque preuve : c'est le moment où l'appareil parle de lui sur sa propre
     * connexion.
     *
     * **Une étiquette qui n'a pas pu se poser n'est pas une panne.** Un
     * annuaire qui ne sert pas encore ce verbe rend `404` ; le compte, lui, est
     * là. On ne le dit pas à l'écran, et l'annuaire réel n'en garde pas trace
     * comme posée — elle repartira à la prochaine preuve.
     *
     * **Portée par la session, pas par l'écran qui a prouvé.** Poser le compte
     * fait quitter l'accueil, dont la portée s'annule ; une description lancée
     * depuis lui tombait à la réponse — l'annuaire avait dit `204`, et l'app ne
     * l'avait pas retenu.
     */
    private fun seDecrire() {
        portee.launch {
            runCatching { annuaire.decrire(Appareil.Description.deCetAppareil()) }
                .onFailure { Log.i("Session", "la description de cet appareil n'a pas été posée : ${it.message}") }
        }
    }

    /**
     * Ouvre le compte : la clé de l'appareil prouve qu'elle est détenue, sur le
     * défi de l'annuaire et la liaison du canal.
     *
     * **C'est ici que la biométrie est demandée**, par le Keystore, au moment
     * de signer — et nulle part avant. Sans confirmation, la clé ne signe pas,
     * et rien ne part.
     */
    suspend fun ouvrirCompte(activite: FragmentActivity, invitation: CodeInvitation? = null) {
        compte = ouverture(invitation) { signataire(activite) }
        seDecrire()
        deposerPoint()
    }

    /**
     * La clé publique à montrer à l'ancien téléphone pour rejoindre son compte.
     * C'est l'annuaire qui la donne : le réel la génère avec le défi de sa
     * connexion, pour que sa chaîne soit présentable à la preuve
     * (`protocole.md` §2.2). La montrer ne demande aucun geste.
     */
    suspend fun clePourRejoindre(activite: FragmentActivity): ByteArray = annuaire.clePourRejoindre { signataire(activite) }

    /** Quitte « rejoindre » sans avoir rejoint : la clé montrée ne servira pas. */
    suspend fun annulerRejoindre() = annuaire.annulerRejoindre()

    /**
     * Rejoint un compte, depuis ce téléphone-ci, avec l'invitation que l'autre
     * a rendue : la preuve et la chaîne, sur la connexion qui a tiré le défi
     * de la clé. Le geste est demandé au moment de prouver.
     *
     * **Porté par la session, et attendu par l'appelant.** Le geste dure ce que
     * dure une empreinte, et poser le compte fait quitter l'écran qui l'a
     * demandé : lancé depuis lui, ce qui suit la preuve tomberait avec sa
     * portée, alors que l'annuaire, lui, a déjà enrôlé et attesté cet appareil.
     * L'attente rend l'issue à l'écran — il a une erreur à montrer et un tour à
     * recommencer —, mais ne la conditionne plus : si l'écran s'en va, le
     * travail finit sans lui.
     */
    suspend fun rejoindre(activite: FragmentActivity, compte: Identifiant, appareil: Identifiant) {
        portee.async {
            this@Session.compte = annuaire.rejoindre(compte, appareil, signataire(activite))
            seDecrire()
            deposerPoint()
        }.await()
    }

    /**
     * Passe à une autre racine ([ChoixDAnnuaire.choisir]) : l'annuaire en
     * cours est fermé, le suivant fabriqué sans se connecter.
     *
     * **Le compte reste** — il est le même sur chaque racine, elles se
     * répliquent —, et avec lui ce que ce téléphone retient des notifications :
     * le point de poussée n'est pas redéposé, il est répliqué (décision 27),
     * et un réveil part de la racine qui écrit l'autorisation, quelle que soit
     * celle que ce téléphone a choisie (décision 9). C'est la relecture que
     * l'écran lance ensuite qui ouvre la connexion, sous l'empreinte : jamais
     * de reconnexion silencieuse.
     */
    suspend fun choisirAnnuaire(racine: RacineDAnnuaire) {
        val choix = choix ?: return
        if (choix.choisir(racine)) annuaire = choix.annuaire
    }

    suspend fun definirAlias(alias: String?) {
        annuaire.definirAlias(alias)
        rafraichirCompte()
    }

    /**
     * Efface le compte — le dernier acte de la clé de cet appareil
     * (`docs/modele.md` §2.1).
     *
     * Dans l'ordre, et l'ordre compte : l'annuaire d'abord (`DELETE
     * /v1/compte`, qui révoque cet appareil avec tout le reste, puis ferme la
     * connexion), et seulement s'il a dit `204` ce qui est ici — la clé,
     * détruite dans le Keystore : l'annuaire l'a déjà révoquée, elle ne
     * signera plus rien qui soit accepté ; et le compte remis à `null`, ce qui
     * ramène l'écran d'accueil ([Racine] n'affiche les onglets que s'il y a
     * un compte). Le carnet local est vidé par l'annuaire lui-même, qui le
     * tient. Si l'annuaire refuse, rien ne bouge ici : l'erreur remonte à
     * l'écran, et le compte reste administrable.
     *
     * **Le geste biométrique n'est pas redemandé pour ce verbe.** Il a été
     * fait pour prouver la connexion qui porte la demande — au lancement, ou
     * à sa réouverture si elle était tombée — et c'est ce que « sous
     * biométrie » veut dire pour l'annuaire (`protocole.md` §2.2 : la
     * confirmation est le geste qui débloque la clé, une fois par connexion).
     * La confirmation de ce qui va partir est celle de l'écran, avant
     * d'appeler ici.
     */
    suspend fun effacerCompte() {
        annuaire.effacerCompte()
        runCatching { effacerCle() }.onFailure { Log.w("Session", "la clé de l'appareil n'a pas pu être détruite ; l'annuaire l'a révoquée", it) }
        compte = null
    }
}

val LocalSession = compositionLocalOf<Session> { error("aucune session") }
