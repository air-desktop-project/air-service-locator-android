package org.airdesktop.servicelocator.composants

import org.airdesktop.servicelocator.modele.Echo
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** L'état d'écho d'une machine (serveur 0.43.0) — les mots de l'application iOS/macOS, à la lettre, cas par cas. */
class TextesEchoEssais {
    private val maintenant = Instant.parse("2026-09-29T12:00:00Z")
    private val il_y_a_3_min = maintenant.minusSeconds(180)
    private val racine = Identifiant.analyser("n-0PWT000000000000000000YHQC", Genre.ANNUAIRE)

    private fun verifie(depuis: Echo.Depuis?, via: String? = null) =
        Echo(Echo.Etat.Verifie, "verifie", il_y_a_3_min, racine, depuis, via)

    @Test
    fun verifieDuDehors() {
        val echo = verifie(Echo.Depuis.Exterieur, "upnp")
        assertEquals("Écho vérifié, du dehors", TextesEcho.libelle(echo))
        assertEquals(Couleurs.joignable, TextesEcho.couleur(echo))
        assertEquals(
            "par la redirection que la box a accordée (UPnP), constaté il y a 3 min, par l'annuaire n-0PWT…YHQC",
            TextesEcho.detail(echo, maintenant),
        )
    }

    @Test
    fun verifieDeLInterieur() {
        val echo = verifie(Echo.Depuis.Interieur, "direct")
        assertEquals("Écho vérifié, de l'intérieur", TextesEcho.libelle(echo))
        assertEquals(Couleurs.accent, TextesEcho.couleur(echo))
        assertEquals(
            "en direct, sans traduction d'adresse, prouvé de son réseau : sa clé répond, ce qui ne dit pas qu'on la joint du dehors, " +
                "constaté il y a 3 min, par l'annuaire n-0PWT…YHQC",
            TextesEcho.detail(echo, maintenant),
        )
    }

    @Test
    fun verifieSansDepuis() {
        val echo = verifie(null, "nat")
        assertEquals("Écho vérifié", TextesEcho.libelle(echo))
        assertEquals(
            "par le NAT que la connexion à l'annuaire tient ouvert, constaté il y a 3 min, par l'annuaire n-0PWT…YHQC",
            TextesEcho.detail(echo, maintenant),
        )
    }

    /** Un `echo_via` qu'on ne connaît pas se dit entre guillemets ; sans `echo_via`, le détail commence à la date. */
    @Test
    fun viaInconnuOuAbsent() {
        assertEquals("par « pcp », constaté il y a 3 min, par l'annuaire n-0PWT…YHQC", TextesEcho.detail(verifie(Echo.Depuis.Exterieur, "pcp"), maintenant))
        assertEquals("constaté il y a 3 min, par l'annuaire n-0PWT…YHQC", TextesEcho.detail(verifie(Echo.Depuis.Exterieur), maintenant))
    }

    /** `echo_via` ne se dit qu'avec un écho vérifié : ailleurs, il ne prouverait rien. */
    @Test
    fun injoignable() {
        val echo = Echo(Echo.Etat.Injoignable, "injoignable", il_y_a_3_min, racine, Echo.Depuis.Exterieur, via = "upnp")
        assertEquals("Écho injoignable", TextesEcho.libelle(echo))
        assertEquals(Couleurs.attention, TextesEcho.couleur(echo))
        assertEquals("constaté il y a 3 min, par l'annuaire n-0PWT…YHQC", TextesEcho.detail(echo, maintenant))
    }

    /** L'autre clé : rouge, et sa phrase ; celle de l'intérieur ne s'y ajoute pas, elle ne parle que d'un écho vérifié. */
    @Test
    fun autreCle() {
        val echo = Echo(Echo.Etat.AutreCle, "autre_cle", il_y_a_3_min, racine, Echo.Depuis.Interieur)
        assertEquals("Écho signé par une autre clé", TextesEcho.libelle(echo))
        assertEquals(Couleurs.erreur, TextesEcho.couleur(echo))
        assertEquals(
            "une réponse est venue, signée par une autre clé que celle de cette machine, constaté il y a 3 min, par l'annuaire n-0PWT…YHQC",
            TextesEcho.detail(echo, maintenant),
        )
    }

    @Test
    fun enCours() {
        val echo = Echo(Echo.Etat.EnCours, "en_cours", par = racine)
        assertEquals("Écho en cours de vérification", TextesEcho.libelle(echo))
        assertEquals(Couleurs.parti, TextesEcho.couleur(echo))
        assertEquals("par l'annuaire n-0PWT…YHQC", TextesEcho.detail(echo, maintenant))
        assertNull(TextesEcho.detail(Echo(Echo.Etat.EnCours, "en_cours"), maintenant))
    }

    @Test
    fun motInconnu() {
        val echo = Echo(Echo.Etat.Inconnu, "sonde_du_futur", il_y_a_3_min)
        assertEquals("Écho : sonde_du_futur", TextesEcho.libelle(echo))
        assertEquals(Couleurs.parti, TextesEcho.couleur(echo))
        assertEquals("constaté il y a 3 min", TextesEcho.detail(echo, maintenant))
    }

    @Test
    fun absent() {
        assertEquals("Pas d'écho", TextesEcho.libelle(null))
        assertEquals("Sur la machine : asl echo", TextesEcho.commentLeLancer)
        assertEquals(Couleurs.parti, TextesEcho.couleur(null))
    }
}
