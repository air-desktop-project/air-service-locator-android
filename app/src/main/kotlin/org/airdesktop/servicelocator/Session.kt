package org.airdesktop.servicelocator

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
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
import org.airdesktop.servicelocator.modele.CodeInvitation
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Signataire
import org.airdesktop.servicelocator.reseau.Annuaire

/**
 * Ce que tous les écrans partagent : l'annuaire à qui parler, et le compte de
 * cet appareil.
 */
class Session(
    val annuaire: Annuaire,
    val identite: IdentiteLocale,
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
     * Comment on ouvre un compte — séparé de l'annuaire parce qu'en
     * démonstration, l'ouverture peuple aussi l'annuaire. La clé est donnée
     * PARESSEUSEMENT : l'annuaire réel la crée lui-même, avec le défi
     * d'attestation de sa connexion, et ne doit pas la trouver déjà faite.
     */
    private val ouverture: suspend (CodeInvitation?, () -> Signataire) -> Compte,
) {
    var compte: Compte? by mutableStateOf(null)
        private set

    /** Relit le compte que l'annuaire connaît pour cet appareil. */
    suspend fun rafraichirCompte() {
        compte = runCatching { annuaire.compte() }.getOrNull()
        if (compte != null) seDecrire()
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
        }.await()
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
