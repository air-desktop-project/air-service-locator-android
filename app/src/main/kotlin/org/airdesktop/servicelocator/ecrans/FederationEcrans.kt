package org.airdesktop.servicelocator.ecrans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.airdesktop.servicelocator.LocalSession
import org.airdesktop.servicelocator.composants.Aide
import org.airdesktop.servicelocator.composants.Badge
import org.airdesktop.servicelocator.composants.BoutonCopier
import org.airdesktop.servicelocator.composants.BoutonDestructif
import org.airdesktop.servicelocator.composants.Couleurs
import org.airdesktop.servicelocator.composants.Erreur
import org.airdesktop.servicelocator.composants.Feuille
import org.airdesktop.servicelocator.composants.FeuilleDeSaisie
import org.airdesktop.servicelocator.composants.Formats
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.composants.LigneAGeste
import org.airdesktop.servicelocator.composants.NoteDeTuile
import org.airdesktop.servicelocator.composants.PiedDestructif
import org.airdesktop.servicelocator.composants.TexteAbsent
import org.airdesktop.servicelocator.composants.TexteFixe
import org.airdesktop.servicelocator.composants.TeteDeTuile
import org.airdesktop.servicelocator.composants.Tuile
import org.airdesktop.servicelocator.composants.messageAnnuaire
import org.airdesktop.servicelocator.composants.rememberChargement
import org.airdesktop.servicelocator.modele.Alias
import org.airdesktop.servicelocator.modele.CodeDInscription
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.EtatDeLAnnuaire
import org.airdesktop.servicelocator.modele.EtatDeLaPaire
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.VoieDuMembre
import java.time.Instant

// Mon annuaire local et l'administration des racines — les pages que le Mac range sous « Fédération »
// (`Sources/Mac/FederationFenetreVue.swift`, PR iOS #36), sous la même règle : une tuile par annuaire, une tuile par
// demande ; ce que l'annuaire fixe se lit ; ce qui se change a son bouton en regard, et se saisit dans une feuille ;
// ce qu'on ne défait pas est en bas, en rouge, avec sa phrase.

// ── Les textes, à la lettre ceux du Mac ──────────────────────────────────────

/**
 * Les mots des pages « Mon annuaire local » et « Administration des racines » — **recopiés de l'application Mac**
 * (`FederationFenetreVue.swift`). Les mots communs à toutes les plates-formes restent dans [TextesDomaines].
 */
internal object TextesFederation {
    /** Ce sous quoi le Mac range les domaines, l'annuaire local et l'administration. */
    const val federation = "Fédération"

    const val introAnnuaireLocal =
        "Une machine de votre compte qui sert elle-même vos domaines, inscrite auprès des racines. Une paire tient au plus deux membres : le titulaire et son secours."
    const val aucunAnnuaireLocal = "Aucun annuaire local"
    const val aucunAnnuaireLocalAide =
        "Déclarez-en un avec « Déclarer un annuaire local… » : l'application donne le code que la machine présentera aux racines."
    const val declarerUnAnnuaire = "Déclarer un annuaire local…"

    const val titulaire = "Titulaire de la paire"
    const val identifiant = "Identifiant"
    const val adresse = "Adresse"
    const val secondMembre = "Second membre"
    const val sansSecours = "aucun — la paire n'a pas de secours"
    const val domainesServis = "Domaines servis"
    const val retirerSecond = "Retirer…"
    const val declarerSecond = "Déclarer…"
    const val confierUnDomaine = "Confier un domaine…"
    const val fixe =
        "L'identifiant et l'adresse sont ceux que la machine a présentés à l'inscription : l'annuaire ne les laisse pas modifier. Changer de machine ou d'adresse, c'est retirer l'annuaire puis en déclarer un nouveau ; entre-temps, ses domaines reviennent aux racines."

    const val declarationEnAttente = "Déclaration en attente de présentation"
    const val code = "Code"
    const val declarationAide = "La machine n'a pas encore présenté son code. Un code perdu se remplace par une nouvelle déclaration."
    fun expire(le: Instant, maintenant: Instant = Instant.now()): String =
        if (le.isAfter(maintenant)) "expire ${Formats.echeance(le, maintenant)}" else "expiré"

    const val confirmerRetraitDuSecond = "Retirer le second membre ?"
    const val retirerLeSecond = "Retirer le second membre"
    const val retraitDuSecond = "La paire reste, servie par son titulaire seul, sans secours."

    const val declarerExplication = "L'application demande aux racines un code d'inscription, que la machine présentera avec sa clé d'identité."
    const val declarerSecondExplication = "Le secours de la paire : une seconde machine, qui présentera ce code avec SA clé."
    const val declarer = "Déclarer"
    const val exempleDAdresse = "[2001:db8::1]:6630"

    const val confierTitre = "Confier un domaine à cet annuaire"
    const val confierExplication = "Il servira le domaine choisi à la place des racines ; le rendre aux racines se fait depuis le domaine."
    const val confier = "Confier"
    const val toutEstConfie = "Tous vos domaines sont déjà servis par cet annuaire."
    const val fermer = "Fermer"

    const val introAdministration =
        "Les annuaires locaux qui demandent à être inscrits auprès des racines. Chaque décision demande confirmation."
    const val aucuneInscriptionAide =
        "Quand un annuaire local demande à être inscrit auprès des racines, sa demande apparaît ici pour être acceptée ou refusée."
    const val nouvellePaire = "Titulaire d'une nouvelle paire"
    const val secondDUnePaire = "Second membre d'une paire"
    const val membre = "Membre"
    const val titulaireCourt = "Titulaire"
    const val demandePar = "Demandé par"

    // L'état de l'annuaire et de sa paire (décisions 70 et 86) — les mêmes libellés que le Mac.
    const val vivant = "Vivant"
    const val parti = "Parti"
    const val pasDeNouvelles = "Pas de nouvelles"
    const val voieOuverte = "Voie ouverte"
    const val voieTombee = "Voie tombée"
    /** Une voie dont la racine n'a rien dit, ou dit un mot qu'on ne connaît pas. */
    const val voieInconnue = "—"
    const val paireReglee = "Paire réglée"
    const val paireMalReglee = "Paire mal réglée"

    fun etat(etat: EtatDeLAnnuaire): String = when (etat) {
        EtatDeLAnnuaire.Vivant -> vivant
        EtatDeLAnnuaire.Parti -> parti
        EtatDeLAnnuaire.PasDeNouvelles -> pasDeNouvelles
    }

    fun voie(voie: VoieDuMembre?): String = when (voie) {
        VoieDuMembre.Ouverte -> voieOuverte
        VoieDuMembre.Tombee -> voieTombee
        VoieDuMembre.Inconnue, null -> voieInconnue
    }

    /**
     * Le membre, nommé dans une phrase par son rôle et son `n-…` abrégé : « Le titulaire (n-7MSV…X87P) », « Le second
     * membre (n-4EQR…08Z9) ». Pas par l'hôte de son adresse : c'est souvent une IPv6, qu'on ne lit pas. L'abrégé est
     * [Identifiant.abrege], celui de toute l'application et, à l'identique, du Mac (`Identifiant.abrege` :
     * six caractères, « n- » compris, « … », quatre derniers) — une même paire se dit pareil sur les deux.
     */
    fun designation(membre: Inscription): String {
        val role = if (membre.estTitulaire) "Le titulaire" else "Le second membre"
        return membre.membre?.let { "$role (${it.abrege})" } ?: role
    }

    /**
     * Ce qu'il faut faire d'une paire mal réglée, sur la machine de [membre] — `null` si rien ne cloche. Le serveur ne
     * refuse pas de démarrer sans `--peer` (décision 70) : c'est ici qu'on le voit, et la phrase dit le geste. Les
     * phrases sont, à la lettre, celles du Mac.
     */
    fun avertissement(membre: Inscription): String? = when (membre.paire) {
        EtatDeLaPaire.SansPeer ->
            "${designation(membre)} tourne sans --peer : la paire ne se réplique pas ; réglez --peer et --peer-key sur cette machine."
        EtatDeLaPaire.PeerInconnu ->
            "${designation(membre)} désigne par --peer un annuaire qui n'est pas l'autre membre de la paire ; corrigez --peer et --peer-key sur cette machine."
        else -> null
    }

    /** « Acceptée », « En attente de la décision des racines »… : l'état, capitalisé, pour un badge. */
    fun badge(inscription: Inscription): String = TextesDomaines.etat(inscription).replaceFirstChar { it.uppercase() }
}

/** La couleur d'un état d'inscription — celle du badge du Mac. */
internal fun couleurDeLEtat(etat: EtatDInscription): Color = when (etat) {
    EtatDInscription.Acceptee -> Couleurs.joignable
    EtatDInscription.Attendue, EtatDInscription.EnAttente -> Couleurs.attention
    EtatDInscription.Refusee -> Couleurs.erreur
    EtatDInscription.Retiree, EtatDInscription.Inconnu -> Couleurs.parti
}

/** La couleur de l'état d'un annuaire : vert s'il tient, orange s'il s'est tu, gris si la racine n'en sait rien. */
internal fun couleurDeLEtat(etat: EtatDeLAnnuaire): Color = when (etat) {
    EtatDeLAnnuaire.Vivant -> Couleurs.joignable
    EtatDeLAnnuaire.Parti -> Couleurs.attention
    EtatDeLAnnuaire.PasDeNouvelles -> Couleurs.parti
}

// ── Ce que la page montre, en fonctions pures ────────────────────────────────

/** Le geste en regard du second membre : le retirer, en déclarer un, ou rien (un code attend sa machine). */
internal sealed interface GesteDuSecond {
    data object Aucun : GesteDuSecond
    data object Declarer : GesteDuSecond
    data class Retirer(val membre: Identifiant) : GesteDuSecond
}

/**
 * La tuile d'un annuaire local : son titulaire, ses seconds (déclarés ou présentés), les domaines qu'il sert, et les
 * gestes permis — tels que le Mac les décide.
 */
internal data class TuileDAnnuaire(
    val titulaire: Inscription,
    /** Le `n-…` de l'annuaire — celui du titulaire. */
    val annuaire: Identifiant,
    val seconds: List<Inscription>,
    val servis: List<Domaine>,
    val gesteDuSecond: GesteDuSecond,
    /** Confier un domaine : seulement à un annuaire accepté (sinon `404`, décision 48). */
    val confierPossible: Boolean,
    /**
     * Vivant, parti, pas de nouvelles — selon la racine qui répond, d'après la voie de chaque membre ; `null` tant que
     * le titulaire n'est pas accepté : la racine ne dit `voie` d'aucune autre inscription, et « pas de nouvelles »
     * d'un annuaire qui n'existe pas encore ne dirait rien.
     */
    val etat: EtatDeLAnnuaire?,
)

/** La page « Mon annuaire local » : une tuile par titulaire, puis les déclarations qui attendent leur machine. */
internal data class PageDAnnuaireLocal(val tuiles: List<TuileDAnnuaire>, val declarations: List<Inscription>) {
    val vide: Boolean get() = tuiles.isEmpty() && declarations.isEmpty()

    companion object {
        fun de(locaux: List<Inscription>, domaines: List<Domaine>): PageDAnnuaireLocal {
            // Une paire retirée ne se gère plus : elle ne fait plus de tuile.
            val tuiles = locaux.filter { it.estTitulaire && it.etat != EtatDInscription.Retiree }.map { titulaire ->
                val n = titulaire.annuaire ?: titulaire.membre!!
                val seconds = locaux.filter { it.annuaire == n && it.membre != n && it.etat != EtatDInscription.Retiree }
                val acceptee = titulaire.etat == EtatDInscription.Acceptee
                // Un second présenté se retire ; sans second, on en déclare un ; un code qui attend sa machine ne
                // laisse ni l'un ni l'autre — l'annuaire compterait déjà deux membres.
                val present = seconds.firstOrNull { it.membre != null }?.membre
                val geste = when {
                    !acceptee -> GesteDuSecond.Aucun
                    present != null -> GesteDuSecond.Retirer(present)
                    seconds.isEmpty() -> GesteDuSecond.Declarer
                    else -> GesteDuSecond.Aucun
                }
                val etat = if (acceptee) EtatDeLAnnuaire.de(listOf(titulaire) + seconds) else null
                TuileDAnnuaire(titulaire, n, seconds, domaines.filter { it.hebergePar == n }, geste, acceptee, etat)
            }
            // Un premier membre déclaré, dont la machine n'a pas encore présenté le code : ni membre, ni annuaire.
            val declarations = locaux.filter { it.membre == null && it.annuaire == null && it.etat == EtatDInscription.Attendue }
            return PageDAnnuaireLocal(tuiles, declarations)
        }

        /** Les domaines qu'on peut confier à [annuaire] : les miens, qu'il ne sert pas déjà. */
        fun aConfier(domaines: List<Domaine>, moi: Identifiant?, annuaire: Identifiant): List<Domaine> =
            domaines.filter { it.proprietaire == moi && it.hebergePar != annuaire }
    }
}

/** Le titre de la tuile d'une demande, pour un administrateur des racines. */
internal fun titreDeLaDemande(inscription: Inscription): String =
    if (inscription.membre == inscription.annuaire) TextesFederation.nouvellePaire else TextesFederation.secondDUnePaire

// ── Mon annuaire local ───────────────────────────────────────────────────────

/** Les feuilles de la page : déclarer (un annuaire, ou le second d'un titulaire), confier un domaine. */
private sealed interface FeuilleDAnnuaire {
    data class Declarer(val titulaire: Identifiant?) : FeuilleDAnnuaire
    data class Confier(val annuaire: Identifiant) : FeuilleDAnnuaire
}

/** Mes annuaires locaux : une tuile par paire, les déclarations qui attendent, et les gestes de chacune. */
@Composable
fun AnnuaireLocalEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    // Les domaines disent ce que chaque annuaire sert ; leur absence n'empêche pas de lire les annuaires.
    val chargement = rememberChargement {
        session.annuaire.annuairesLocaux() to runCatching { session.annuaire.domaines() }.getOrDefault(emptyList())
    }
    val domaines = chargement.valeur?.second.orEmpty()
    val page = chargement.valeur?.let { (locaux, d) -> PageDAnnuaireLocal.de(locaux, d) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var feuille by remember { mutableStateOf<FeuilleDAnnuaire?>(null) }
    var aRetirer by remember { mutableStateOf<Identifiant?>(null) }
    // Le second à retirer : (annuaire, membre).
    var secondARetirer by remember { mutableStateOf<Pair<Identifiant, Identifiant>?>(null) }

    fun faire(geste: suspend () -> Unit) = portee.launch {
        runCatching { geste() }.onSuccess { erreur = null; chargement.recharger() }.onFailure { erreur = it.messageAnnuaire }
    }

    Scaffold(
        topBar = { Barre(TextesDomaines.annuaireLocal, nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { feuille = FeuilleDAnnuaire.Declarer(null) },
                icon = { Icon(Icones.plus, null) },
                text = { Text(TextesFederation.declarerUnAnnuaire) },
            )
        },
    ) { marges ->
        // Le bas de la liste laisse passer le bouton flottant : le pied rouge de la dernière tuile ne doit pas s'y cacher.
        LazyColumn(Modifier.fillMaxSize().padding(marges), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Aide(TextesFederation.introAnnuaireLocal) }
            item { Erreur(chargement.erreur ?: erreur) }
            if (page?.vide == true) item { EtatVide(TextesFederation.aucunAnnuaireLocal, TextesFederation.aucunAnnuaireLocalAide) }
            items(page?.tuiles.orEmpty(), key = { it.annuaire.texte }) { tuile ->
                TuileDeLAnnuaire(
                    tuile,
                    onRetirerSecond = { membre -> secondARetirer = tuile.annuaire to membre },
                    onDeclarerSecond = { feuille = FeuilleDAnnuaire.Declarer(tuile.annuaire) },
                    onConfier = { feuille = FeuilleDAnnuaire.Confier(tuile.annuaire) },
                    onRetirer = { aRetirer = tuile.annuaire },
                )
            }
            items(page?.declarations.orEmpty()) { declaration -> TuileDeDeclaration(declaration) }
        }
    }

    when (val f = feuille) {
        is FeuilleDAnnuaire.Declarer -> FeuilleDeDeclaration(f.titulaire, onFermer = { feuille = null }, onDeclare = { chargement.recharger() })
        is FeuilleDAnnuaire.Confier -> FeuilleDeConfiance(
            candidats = PageDAnnuaireLocal.aConfier(domaines, session.compte?.identifiant, f.annuaire),
            annuaire = f.annuaire,
            onFermer = { feuille = null },
            onConfie = { chargement.recharger() },
        )
        null -> Unit
    }
    aRetirer?.let { n ->
        Confirmation(
            titre = TextesDomaines.retirer, texte = TextesDomaines.confirmerRetrait, action = TextesDomaines.retirer,
            onAnnuler = { aRetirer = null },
            onConfirmer = { aRetirer = null; faire { session.annuaire.retirerAnnuaire(n) } },
        )
    }
    secondARetirer?.let { (n, membre) ->
        Confirmation(
            titre = TextesFederation.confirmerRetraitDuSecond, texte = TextesFederation.retraitDuSecond, action = TextesFederation.retirerLeSecond,
            onAnnuler = { secondARetirer = null },
            onConfirmer = { secondARetirer = null; faire { session.annuaire.retirerMembre(n, membre) } },
        )
    }
}

@Composable
private fun TuileDeLAnnuaire(
    tuile: TuileDAnnuaire,
    onRetirerSecond: (Identifiant) -> Unit,
    onDeclarerSecond: () -> Unit,
    onConfier: () -> Unit,
    onRetirer: () -> Unit,
) {
    val titulaire = tuile.titulaire
    Tuile {
        TeteDeTuile(TextesFederation.titulaire) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tuile.etat?.let { Badge(TextesFederation.etat(it), couleurDeLEtat(it)) }
                BadgeDeLEtat(titulaire)
            }
        }
        titulaire.membre?.let { membre ->
            LigneAGeste(TextesFederation.identifiant, geste = { BoutonCopier(membre.texte) }) { TexteFixe(membre.texte) }
        }
        LigneAGeste(TextesFederation.adresse, geste = { BoutonCopier(titulaire.adresse) }) {
            AdresseEtVoie(titulaire, tuile.etat != null)
            EtatDeLaPaireDuMembre(titulaire)
        }
        LigneAGeste(
            TextesFederation.secondMembre,
            geste = when (val geste = tuile.gesteDuSecond) {
                is GesteDuSecond.Retirer -> ({ TextButton(onClick = { onRetirerSecond(geste.membre) }) { Text(TextesFederation.retirerSecond) } })
                GesteDuSecond.Declarer -> ({ TextButton(onClick = onDeclarerSecond) { Text(TextesFederation.declarerSecond) } })
                GesteDuSecond.Aucun -> null
            },
        ) {
            if (tuile.seconds.isEmpty()) TexteAbsent(TextesFederation.sansSecours)
            tuile.seconds.forEach { second ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f, fill = false)) { TexteFixe(second.membre?.texte ?: second.adresse) }
                    BadgeDeLEtat(second)
                }
                if (second.membre != null) {
                    AdresseEtVoie(second, second.etat == EtatDInscription.Acceptee, secondaire = true)
                    EtatDeLaPaireDuMembre(second)
                }
            }
        }
        LigneAGeste(
            TextesFederation.domainesServis,
            geste = if (tuile.confierPossible) ({ TextButton(onClick = onConfier) { Text(TextesFederation.confierUnDomaine) } }) else null,
        ) {
            if (tuile.servis.isEmpty()) TexteAbsent(TextesDomaines.aucun)
            tuile.servis.forEach { Text(it.affiche, style = MaterialTheme.typography.bodyMedium) }
        }
        NoteDeTuile(TextesFederation.fixe)
        PiedDestructif(TextesDomaines.confirmerRetrait, "${TextesDomaines.retirer}…", action = onRetirer)
    }
}

@Composable
private fun TuileDeDeclaration(declaration: Inscription) {
    Tuile {
        TeteDeTuile(TextesFederation.declarationEnAttente) { BadgeDeLEtat(declaration) }
        LigneAGeste(TextesFederation.adresse, geste = { BoutonCopier(declaration.adresse) }) { TexteFixe(declaration.adresse) }
        declaration.expireA?.let { LigneAGeste(TextesFederation.code) { Text(TextesFederation.expire(it), style = MaterialTheme.typography.bodyMedium) } }
        NoteDeTuile(TextesFederation.declarationAide)
    }
}

/**
 * L'adresse d'un membre, et à côté, sa voie vers la racine qui répond — « — » quand elle n'en dit rien. Un membre qui
 * n'est pas accepté n'a pas de voie : on ne la montre pas.
 */
@Composable
private fun AdresseEtVoie(membre: Inscription, avecVoie: Boolean, secondaire: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f, fill = false)) { TexteFixe(membre.adresse, secondaire) }
        if (avecVoie) {
            val couleur = when (membre.voie) {
                VoieDuMembre.Ouverte -> Couleurs.joignable
                VoieDuMembre.Tombee -> Couleurs.attention
                VoieDuMembre.Inconnue, null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(TextesFederation.voie(membre.voie), style = MaterialTheme.typography.labelMedium, color = couleur)
        }
    }
}

/**
 * Ce que ce membre conclut de sa paire : une coche discrète si elle est réglée, un avertissement rouge qui dit le
 * geste si elle ne se réplique pas ; rien s'il est seul, s'il ne l'a pas encore dit, ou dit un mot inconnu.
 */
@Composable
private fun EtatDeLaPaireDuMembre(membre: Inscription) {
    val paire = membre.paire
    val avertissement = TextesFederation.avertissement(membre)
    when {
        avertissement != null -> Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icones.alerte, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Column {
                Text(TextesFederation.paireMalReglee, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Text(avertissement, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        paire == EtatDeLaPaire.Reglee -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icones.coche, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(TextesFederation.paireReglee, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Unit
    }
}

@Composable
private fun BadgeDeLEtat(inscription: Inscription) = Badge(TextesFederation.badge(inscription), couleurDeLEtat(inscription.etat))

/** Déclarer (un annuaire, ou le second membre), puis montrer le code et la commande, dans la même feuille. */
@Composable
private fun FeuilleDeDeclaration(titulaire: Identifiant?, onFermer: () -> Unit, onDeclare: () -> Unit) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    var adresse by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<CodeDInscription?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val forme = Alias.adresseDAnnuaire(adresse)

    val obtenu = code
    if (obtenu != null) {
        // La racine à laquelle l'application parle : c'est elle que la machine joindra pour s'inscrire.
        val racine = session.choix?.choisie?.affichePourLeJournal ?: "<racine>"
        val commande = TextesDomaines.commande(obtenu.code, racine)
        Feuille(onFermer) {
            Text(TextesDomaines.codeTitre, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(obtenu.code, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(TextesFederation.expire(obtenu.expireA), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(TextesDomaines.codeAide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(commande, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    BoutonCopier(commande)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onFermer) { Text(TextesFederation.fermer) }
            }
        }
        return
    }
    FeuilleDeSaisie(
        titre = if (titulaire == null) TextesDomaines.declarer else TextesDomaines.secondMembre,
        explication = if (titulaire == null) TextesFederation.declarerExplication else TextesFederation.declarerSecondExplication,
        action = TextesFederation.declarer,
        actionPermise = forme != null,
        enCours = enCours,
        erreur = erreur,
        onFermer = onFermer,
        valider = {
            val a = forme ?: return@FeuilleDeSaisie
            enCours = true
            portee.launch {
                runCatching {
                    if (titulaire == null) session.annuaire.declarerAnnuaire(a) else session.annuaire.declarerSecondMembre(titulaire, a)
                }.onSuccess { code = it; erreur = null; onDeclare() }.onFailure { erreur = it.messageAnnuaire }
                enCours = false
            }
        },
    ) {
        OutlinedTextField(
            adresse, { adresse = it }, label = { Text(TextesDomaines.adresse) }, placeholder = { Text(TextesFederation.exempleDAdresse) },
            singleLine = true, isError = adresse.isNotEmpty() && forme == null, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            supportingText = { Text(TextesDomaines.adresseAide) }, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Confier l'un de mes domaines à cet annuaire. Le rendre aux racines se fait depuis le domaine. */
@Composable
private fun FeuilleDeConfiance(candidats: List<Domaine>, annuaire: Identifiant, onFermer: () -> Unit, onConfie: () -> Unit) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    var choisi by remember { mutableStateOf<Identifiant?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    FeuilleDeSaisie(
        titre = TextesFederation.confierTitre,
        explication = TextesFederation.confierExplication,
        action = TextesFederation.confier,
        actionPermise = choisi != null,
        enCours = enCours,
        erreur = erreur,
        onFermer = onFermer,
        valider = {
            val domaine = choisi ?: return@FeuilleDeSaisie
            enCours = true
            portee.launch {
                runCatching { session.annuaire.confier(domaine, annuaire) }
                    .onSuccess { onConfie(); onFermer() }
                    .onFailure { erreur = it.messageAnnuaire }
                enCours = false
            }
        },
    ) {
        if (candidats.isEmpty()) TexteAbsent(TextesFederation.toutEstConfie)
        candidats.forEach { domaine ->
            Row(Modifier.fillMaxWidth().clickable { choisi = domaine.id }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = choisi == domaine.id, onClick = { choisi = domaine.id })
                Text(titreComplet(domaine), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ── Administration des racines ───────────────────────────────────────────────

/** Les inscriptions qui attendent un administrateur des racines — cet écran n'existe que pour eux. */
@Composable
fun AdministrationDesRacinesEcran(nav: NavController) {
    val session = LocalSession.current
    val portee = rememberCoroutineScope()
    val chargement = rememberChargement { session.annuaire.inscriptionsEnAttente() }
    var erreur by remember { mutableStateOf<String?>(null) }
    // La décision à confirmer : l'inscription, et accepter ou refuser.
    var decision by remember { mutableStateOf<Pair<Inscription, Boolean>?>(null) }

    Scaffold(topBar = { Barre(TextesDomaines.administration, nav) }) { marges ->
        LazyColumn(Modifier.fillMaxSize().padding(marges), contentPadding = PaddingValues(bottom = 24.dp)) {
            val liste = chargement.valeur
            item { Erreur(chargement.erreur ?: erreur) }
            if (liste == null) return@LazyColumn
            if (liste.isEmpty() && erreur == null) {
                item { EtatVide(TextesDomaines.aucuneInscription.removeSuffix("."), TextesFederation.aucuneInscriptionAide) }
                return@LazyColumn
            }
            item { Aide(TextesFederation.introAdministration) }
            items(liste) { inscription ->
                TuileDeDemande(inscription, onDecider = { accepte -> decision = inscription to accepte })
            }
        }
    }

    decision?.let { (inscription, accepte) ->
        Confirmation(
            titre = if (accepte) TextesDomaines.accepter else TextesDomaines.refuser,
            texte = if (accepte) TextesDomaines.confirmerAcceptation(
                inscription.membre?.texte ?: "?", inscription.adresse, inscription.proprietaire?.texte ?: "?",
            ) else TextesDomaines.confirmerRefus,
            action = if (accepte) TextesDomaines.accepter else TextesDomaines.refuser,
            destructif = !accepte,
            onAnnuler = { decision = null },
            onConfirmer = {
                decision = null
                val membre = inscription.membre ?: return@Confirmation
                portee.launch {
                    runCatching { session.annuaire.decider(membre, accepte) }
                        .onSuccess { erreur = null; chargement.recharger() }
                        .onFailure { erreur = it.messageAnnuaire }
                }
            },
        )
    }
}

@Composable
private fun TuileDeDemande(inscription: Inscription, onDecider: (Boolean) -> Unit) {
    Tuile {
        TeteDeTuile(titreDeLaDemande(inscription)) {
            Badge(TextesFederation.badge(inscription.copy(etat = EtatDInscription.EnAttente)), couleurDeLEtat(EtatDInscription.EnAttente))
        }
        inscription.membre?.let { LigneAGeste(TextesFederation.membre, geste = { BoutonCopier(it.texte) }) { TexteFixe(it.texte) } }
        if (inscription.membre != inscription.annuaire) inscription.annuaire?.let {
            LigneAGeste(TextesFederation.titulaireCourt, geste = { BoutonCopier(it.texte) }) { TexteFixe(it.texte) }
        }
        LigneAGeste(TextesFederation.adresse, geste = { BoutonCopier(inscription.adresse) }) { TexteFixe(inscription.adresse) }
        inscription.proprietaire?.let { LigneAGeste(TextesFederation.demandePar, geste = { BoutonCopier(it.texte) }) { TexteFixe(it.texte) } }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            BoutonDestructif("${TextesDomaines.refuser}…") { onDecider(false) }
            Button(onClick = { onDecider(true) }) { Text("${TextesDomaines.accepter}…") }
        }
    }
}

// ── Les morceaux communs aux deux pages ──────────────────────────────────────

/** Un état vide soigné : un titre, et ce qui fera apparaître quelque chose ici. */
@Composable
internal fun EtatVide(titre: String, aide: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icones.machine, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(aide, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Confirmer un geste : sa phrase, Annuler, et l'action — en rouge si on ne la défait pas. */
@Composable
internal fun Confirmation(
    titre: String,
    texte: String,
    action: String,
    destructif: Boolean = true,
    onAnnuler: () -> Unit,
    onConfirmer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = { Text(texte) },
        confirmButton = {
            TextButton(onClick = onConfirmer) {
                Text(action, color = if (destructif) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}
