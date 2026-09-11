package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Candidat
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.Compte
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Joignabilite
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import java.time.Instant

/**
 * Un annuaire déjà peuplé de ce que les maquettes montraient : un compte, cinq
 * machines, des services dans chaque état, des autorisations dans les deux
 * sens. **Ce sont des données inventées**, et l'écran d'accueil reste le
 * premier écran : le compte n'est ouvert que quand l'utilisateur le fait.
 */
object Demonstration {
    /** Ouvre le compte ET pose les données, pour que la suite ait quelque chose à montrer. */
    suspend fun ouvrirCompte(annuaire: AnnuaireSimule, cle: ByteArray, preuve: ByteArray): Compte {
        val compte = annuaire.ouvrirCompte(cle, preuve)
        if (annuaire.machines().isNotEmpty()) return compte
        peupler(annuaire, compte)
        return compte
    }

    private suspend fun peupler(annuaire: AnnuaireSimule, compte: Compte) {
        val maintenant = Instant.now()
        fun ilYA(secondes: Long): Instant = maintenant.minusSeconds(secondes)
        fun id(genre: Genre, graine: Int) = Identifiant(genre, ByteArray(16) { (it * 37 + graine * 11 + 5).toByte() })
        fun tcp(port: Int) = PointEcoute(PointEcoute.Protocole.TCP, port)
        fun udp(port: Int) = PointEcoute(PointEcoute.Protocole.UDP, port)
        val jour = 86_400L

        val vero = id(Genre.UTILISATEUR, 1); val marc = id(Genre.UTILISATEUR, 2)
        val collegue = id(Genre.UTILISATEUR, 3); val test = id(Genre.UTILISATEUR, 4)
        annuaire.inscrireAutreCompte(vero, "vero")
        annuaire.inscrireAutreCompte(marc, "marc")
        annuaire.inscrireAutreCompte(collegue, null)
        annuaire.inscrireAutreCompte(test, null)

        val grenier = Machine(
            id(Genre.MACHINE, 10), "grenier", setOf(Capacite.ANNONCE), Machine.Cle.Enrolee(ilYA(7 * jour)),
            listOf(
                Service(
                    id(Genre.SERVICE, 11), "depot-de-messages", listOf(tcp(49_152), udp(49_152)),
                    Service.Etat.Annonce(ilYA(3_600)),
                    mapOf(tcp(49_152) to Joignabilite.Joignable(ilYA(120), "[2001:db8::1c2d]:49152"), udp(49_152) to Joignabilite.NonSonde),
                    listOf(
                        Candidat(PointEcoute.Protocole.TCP, "2001:db8::1c2d", 49_152, Candidat.Origine.REFLEXIF),
                        Candidat(PointEcoute.Protocole.TCP, "192.168.1.20", 49_152, Candidat.Origine.ANNONCE),
                    ),
                ),
                Service(
                    id(Genre.SERVICE, 12), "sauvegarde", listOf(tcp(8_443)), Service.Etat.Annonce(ilYA(900)),
                    mapOf(tcp(8_443) to Joignabilite.Injoignable(ilYA(300))),
                    listOf(Candidat(PointEcoute.Protocole.TCP, "203.0.113.4", 8_443, Candidat.Origine.REFLEXIF)),
                ),
                Service(
                    id(Genre.SERVICE, 13), "metriques", listOf(udp(9_100)), Service.Etat.Annonce(ilYA(3_600)),
                    mapOf(udp(9_100) to Joignabilite.NonSonde),
                    listOf(Candidat(PointEcoute.Protocole.UDP, "203.0.113.4", 9_100, Candidat.Origine.REFLEXIF)),
                ),
            ),
        )
        val bureau = Machine(
            id(Genre.MACHINE, 20), "bureau", setOf(Capacite.ANNONCE, Capacite.LECTURE), Machine.Cle.Enrolee(ilYA(5 * jour)),
            listOf(
                Service(
                    id(Genre.SERVICE, 21), "depot-de-messages", listOf(tcp(49_160)), Service.Etat.Annonce(ilYA(60)),
                    mapOf(tcp(49_160) to Joignabilite.EnCours),
                    listOf(Candidat(PointEcoute.Protocole.TCP, "2001:db8::77", 49_160, Candidat.Origine.REFLEXIF)),
                ),
            ),
        )
        val portable = Machine(id(Genre.MACHINE, 30), "portable-vero", setOf(Capacite.LECTURE), Machine.Cle.Enrolee(ilYA(2 * jour)))
        val nas = Machine(
            id(Genre.MACHINE, 40), "nas", setOf(Capacite.ANNONCE), Machine.Cle.Enrolee(ilYA(30 * jour)),
            listOf(
                Service(
                    id(Genre.SERVICE, 41), "partage", listOf(tcp(445)), Service.Etat.Annonce(ilYA(12)),
                    mapOf(tcp(445) to Joignabilite.Joignable(ilYA(10), "[2001:db8::40]:445")),
                    listOf(Candidat(PointEcoute.Protocole.TCP, "2001:db8::40", 445, Candidat.Origine.REFLEXIF)),
                    oscille = true,
                ),
                Service(id(Genre.SERVICE, 42), "horloge", listOf(udp(123)), Service.Etat.Parti(volontaire = true, le = ilYA(jour + 3_000))),
            ),
        )
        val cave = Machine(
            id(Genre.MACHINE, 50), "serveur-cave", setOf(Capacite.ANNONCE),
            Machine.Cle.Attendue(CodeEnrolement.depuisEntropie(byteArrayOf(0x4A, 0x6E, 0x9D.toByte(), 0x1C, 0x83.toByte(), 0x2F, 0xD0.toByte(), 0x00), ilYA(240))),
        )
        for (machine in listOf(cave, grenier, bureau, portable, nas)) annuaire.poser(machine)

        annuaire.poser(Appareil(id(Genre.APPAREIL, 60), "iPhone de Thierry", Appareil.Biometrie.VISAGE, ilYA(5 * jour)))
        annuaire.poser(Appareil(id(Genre.APPAREIL, 61), "iPad", Appareil.Biometrie.EMPREINTE, ilYA(40 * jour), revoqueLe = ilYA(3 * jour)))

        val moi = compte.identifiant
        annuaire.recevoir(Autorisation(id(Genre.AUTORISATION, 70), moi, vero, Autorisation.Portee.Tout, "maison", ilYA(20 * jour)))
        annuaire.recevoir(Autorisation(id(Genre.AUTORISATION, 71), moi, collegue, Autorisation.Portee.Machine(grenier.id), "collègue", ilYA(10 * jour)))
        annuaire.recevoir(Autorisation(id(Genre.AUTORISATION, 72), moi, test, Autorisation.Portee.Service(id(Genre.SERVICE, 12)), "test", ilYA(15 * jour), revoqueeLe = ilYA(9 * jour)))
        annuaire.recevoir(Autorisation(id(Genre.AUTORISATION, 73), marc, moi, Autorisation.Portee.Tout, "", ilYA(2 * jour)))
    }
}
