package org.airdesktop.servicelocator.modele

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La forme d'un point de poussée, telle que l'annuaire l'exige (`protocole.md` §2.2). */
class PointDePousseeEssais {
    @Test
    fun unPointDeDistributeurOrdinairePasse() {
        assertNull(PointDePoussee.refus("https://ntfy.sh/upAb3kZq9xYz?up=1"))
        assertNull(PointDePoussee.refus("https://ntfy.example.org:443/upAb3kZq9"))
        assertNull(PointDePoussee.refus("https://push.example.org"))
    }

    @Test
    fun ceQueLAnnuaireRefuseEstRefuseIciAvecSaRegle() {
        assertEquals("https:// et rien d'autre", PointDePoussee.refus("http://ntfy.example.org/up"))
        assertEquals("le port 443, implicite ou écrit", PointDePoussee.refus("https://ntfy.example.org:8080/up"))
        assertEquals("un nom DNS, pas une adresse", PointDePoussee.refus("https://192.168.1.10/up"))
        assertEquals("un nom DNS, pas une adresse", PointDePoussee.refus("https://0x7f000001/up"))
        assertEquals("un nom DNS, pas une adresse", PointDePoussee.refus("https://[2001:db8::1]/up"))
        assertEquals("pas d'identifiants", PointDePoussee.refus("https://moi@ntfy.example.org/up"))
        assertEquals("pas de fragment", PointDePoussee.refus("https://ntfy.example.org/up#x"))
        assertEquals("de l'ASCII imprimable, sans espace", PointDePoussee.refus("https://ntfy.example.org/u p"))
        assertEquals("un nom DNS : lettres, chiffres, tirets, points", PointDePoussee.refus("https://-ntfy.example.org/up"))
        assertEquals("1024 octets au plus", PointDePoussee.refus("https://ntfy.example.org/" + "a".repeat(1000)))
    }

    @Test
    fun leCorpsEstCeluiQueLAnnuaireLit() {
        assertEquals(
            """{"plateforme":"unifiedpush","point":"https://ntfy.example.org/upAb3kZq9"}""",
            PointDePoussee.corps("https://ntfy.example.org/upAb3kZq9"),
        )
        // Rien d'autre ne s'échappe dans un point dont la forme tient.
        assertEquals("""{"plateforme":"unifiedpush","point":"https://h.example/a\"b\\c"}""", PointDePoussee.corps("https://h.example/a\"b\\c"))
    }
}
