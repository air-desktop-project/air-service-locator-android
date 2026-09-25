package org.airdesktop.servicelocator

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.airdesktop.servicelocator.composants.Icones
import org.airdesktop.servicelocator.ecrans.AccesEcran
import org.airdesktop.servicelocator.ecrans.AccorderEcran
import org.airdesktop.servicelocator.ecrans.AccueilEcran
import org.airdesktop.servicelocator.ecrans.AliasEcran
import org.airdesktop.servicelocator.ecrans.CapacitesEcran
import org.airdesktop.servicelocator.ecrans.CodeEnrolementEcran
import org.airdesktop.servicelocator.ecrans.CompteEcran
import org.airdesktop.servicelocator.ecrans.DeclarerMachineEcran
import org.airdesktop.servicelocator.ecrans.EnrolerAppareilEcran
import org.airdesktop.servicelocator.ecrans.ExpositionsEcran
import org.airdesktop.servicelocator.ecrans.MachineEcran
import org.airdesktop.servicelocator.ecrans.MachinesEcran
import org.airdesktop.servicelocator.ecrans.RejoindreEcran
import org.airdesktop.servicelocator.ecrans.ServiceEcran
import org.airdesktop.servicelocator.modele.Identifiant

/** Les destinations. Un identifiant voyage sous sa forme canonique, et se relit à l'arrivée. */
object Routes {
    const val MACHINES = "machines"
    const val ACCES = "acces"
    const val COMPTE = "compte"
    const val DECLARER = "machines/declarer"
    const val ACCORDER = "acces/accorder"
    const val ALIAS = "compte/alias"
    const val ENROLER_APPAREIL = "compte/enroler-appareil"
    const val EXPOSITIONS = "compte/expositions"
    fun machine(id: Identifiant) = "machines/${id.texte}"
    fun code(id: Identifiant) = "machines/${id.texte}/code"
    fun capacites(id: Identifiant) = "machines/${id.texte}/capacites"
    fun service(machine: Identifiant, service: Identifiant) = "machines/${machine.texte}/services/${service.texte}"
}

private data class Onglet(val route: String, val libelle: String, val icone: ImageVector)

private val onglets = listOf(
    Onglet(Routes.MACHINES, "Machines", Icones.machine),
    Onglet(Routes.ACCES, "Accès", Icones.acces),
    Onglet(Routes.COMPTE, "Compte", Icones.compte),
)

/** Accueil tant qu'il n'y a pas de compte, les onglets ensuite. */
@Composable
fun Racine() {
    val session = LocalSession.current
    LaunchedEffect(Unit) { session.rafraichirCompte() }
    // La relecture à l'ouverture : dès que le compte est connu, ce qu'on a reçu depuis la dernière fois.
    val compte = session.compte?.identifiant
    LaunchedEffect(compte) { if (compte != null) session.relire() }
    // Retenu à travers une rotation : la clé montrée et la connexion qui tient son défi vivent dans l'annuaire, pas dans l'écran.
    var rejoindre by rememberSaveable { mutableStateOf(false) }
    if (session.compte == null) {
        if (rejoindre) RejoindreEcran(retour = { rejoindre = false }) else AccueilEcran(surRejoindre = { rejoindre = true })
    } else {
        Onglets()
    }
}

@Composable
private fun Onglets() {
    val nav = rememberNavController()
    val pile by nav.currentBackStackEntryAsState()
    val routeCourante = pile?.destination?.route
    val racine = onglets.any { it.route == routeCourante }

    Scaffold(
        bottomBar = {
            if (racine) {
                NavigationBar {
                    for (onglet in onglets) {
                        NavigationBarItem(
                            selected = routeCourante == onglet.route,
                            onClick = {
                                nav.navigate(onglet.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                // La pastille dit combien d'accès reçus l'écran n'a pas encore montrés — la relecture
                                // à l'ouverture, visible sans aller la chercher.
                                val nouveautes = if (onglet.route == Routes.ACCES) LocalSession.current.nouveautes else 0
                                BadgedBox(badge = { if (nouveautes > 0) Badge { Text("$nouveautes") } }) { Icon(onglet.icone, null) }
                            },
                            label = { Text(onglet.libelle) },
                        )
                    }
                }
            }
        },
    ) { marges ->
        NavHost(nav, startDestination = Routes.MACHINES, modifier = Modifier.padding(marges)) {
            composable(Routes.MACHINES) { MachinesEcran(nav) }
            composable(Routes.DECLARER) { DeclarerMachineEcran(nav) }
            composable("machines/{id}") { MachineEcran(nav, Identifiant.analyser(it.arguments!!.getString("id")!!)) }
            composable("machines/{id}/code") { CodeEnrolementEcran(nav, Identifiant.analyser(it.arguments!!.getString("id")!!)) }
            composable("machines/{id}/capacites") { CapacitesEcran(nav, Identifiant.analyser(it.arguments!!.getString("id")!!)) }
            composable("machines/{id}/services/{s}") {
                ServiceEcran(nav, Identifiant.analyser(it.arguments!!.getString("id")!!), Identifiant.analyser(it.arguments!!.getString("s")!!))
            }
            composable(Routes.ACCES) { AccesEcran(nav) }
            composable(Routes.ACCORDER) { AccorderEcran(nav) }
            composable(Routes.COMPTE) { CompteEcran(nav) }
            composable(Routes.ALIAS) { AliasEcran(nav) }
            composable(Routes.ENROLER_APPAREIL) { EnrolerAppareilEcran(nav) }
            composable(Routes.EXPOSITIONS) { ExpositionsEcran(nav) }
        }
    }
}
