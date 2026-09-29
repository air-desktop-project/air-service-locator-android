package org.airdesktop.servicelocator.reseau

import org.airdesktop.servicelocator.modele.Echo
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** L'état d'écho tel que le serveur 0.43.0 l'écrit sur une machine — et tel qu'un annuaire plus récent, ou fautif, l'écrirait. */
class LectureDeLEchoEssais {
    private val n = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 3 }).texte
    private val u = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 2 }).texte
    private val m = Identifiant(Genre.MACHINE, ByteArray(16) { 5 }).texte
    private val d = Identifiant(Genre.DOMAINE, ByteArray(16) { 1 }).texte

    private fun lire(json: String) = LectureDeLEcho.echo(JSONObject(json))

    @Test
    fun toutPresent() {
        val echo = lire("""{"echo":"verifie","echo_a":1789217751000,"echo_par":"$n","echo_depuis":"exterieur","echo_via":"upnp"}""")!!
        assertEquals(Echo.Etat.Verifie, echo.etat)
        assertEquals("verifie", echo.mot)
        assertEquals(Instant.ofEpochMilli(1789217751000), echo.a)
        assertEquals(n, echo.par?.texte)
        assertEquals(Echo.Depuis.Exterieur, echo.depuis)
        assertEquals("upnp", echo.via)
    }

    @Test
    fun chaqueMotConnu() {
        assertEquals(Echo.Etat.Injoignable, lire("""{"echo":"injoignable"}""")?.etat)
        assertEquals(Echo.Etat.AutreCle, lire("""{"echo":"autre_cle"}""")?.etat)
        assertEquals(Echo.Etat.EnCours, lire("""{"echo":"en_cours"}""")?.etat)
        assertEquals(Echo.Depuis.Interieur, lire("""{"echo":"verifie","echo_depuis":"interieur"}""")?.depuis)
    }

    /** Pas d'`echo` — une machine sans `asl echo`, ou un annuaire d'avant 0.43.0 — : rien, et les autres clés n'y changent rien. */
    @Test
    fun absent() {
        assertNull(lire("""{}"""))
        assertNull(lire("""{"echo_a":1789217751000,"echo_par":"$n"}"""))
        assertNull(lire("""{"echo":null}"""))
        assertNull(lire("""{"echo":""}"""))
    }

    /** Un champ d'une autre forme se lit comme absent, un par un ; il n'emporte ni la machine ni les autres champs. */
    @Test
    fun autresTypes() {
        assertNull(lire("""{"echo":1}"""))
        assertNull(lire("""{"echo":{"etat":"verifie"}}"""))
        val echo = lire("""{"echo":"verifie","echo_a":"1789217751000","echo_par":42,"echo_depuis":true,"echo_via":["upnp"]}""")!!
        assertEquals(Echo.Etat.Verifie, echo.etat)
        assertNull(echo.a)
        assertNull(echo.par)
        assertNull(echo.depuis)
        assertNull(echo.via)
        assertNull(lire("""{"echo":"verifie","echo_a":1.5}""")!!.a)
        assertEquals(Instant.ofEpochMilli(12), lire("""{"echo":"verifie","echo_a":12}""")!!.a)
        // Un `n-…` illisible, ou d'un autre genre, n'est pas un annuaire.
        assertNull(lire("""{"echo":"verifie","echo_par":"n-pas-un-id"}""")!!.par)
        assertNull(lire("""{"echo":"verifie","echo_par":"$u"}""")!!.par)
    }

    /** Un mot d'un annuaire plus récent : gardé tel quel pour l'état et pour `echo_via` ; oublié pour `echo_depuis`. */
    @Test
    fun motsInconnus() {
        val echo = lire("""{"echo":"sonde_du_futur","echo_depuis":"ailleurs","echo_via":"pcp"}""")!!
        assertEquals(Echo.Etat.Inconnu, echo.etat)
        assertEquals("sonde_du_futur", echo.mot)
        assertNull(echo.depuis)
        assertEquals("pcp", echo.via)
    }

    /** Les deux endroits où l'annuaire l'écrit : la machine d'un domaine le porte aussi. */
    @Test
    fun dansLeDetailDUnDomaine() {
        val corps = """{"domaine":"$d","proprietaire":"$u","droits":["voir"],"machines":[""" +
            """{"machine":"$m","proprietaire":"$u","nom":"nitrogen","echo":"injoignable","echo_a":1789217751000,"echo_par":"$n"},""" +
            """{"machine":"$m","proprietaire":"$u","nom":"argon"}]}"""
        val detail = LectureDesDomaines.detail(corps)!!
        val echo = detail.machines[0].echo
        assertNotNull(echo)
        assertEquals(Echo.Etat.Injoignable, echo!!.etat)
        assertEquals(n, echo.par?.texte)
        assertNull(detail.machines[1].echo)
    }
}
