package org.airdesktop.servicelocator.reseau

import kotlinx.coroutines.runBlocking
import org.airdesktop.servicelocator.modele.Appareil
import org.airdesktop.servicelocator.modele.Autorisation
import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CleLogicielle
import org.airdesktop.servicelocator.modele.NonConfirmeException
import org.airdesktop.servicelocator.modele.Signataire
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.PointEcoute
import org.airdesktop.servicelocator.modele.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Les refus de `docs/protocole.md` §2, tenus par le banc. */
class AnnuaireSimuleEssais {
    private val instant = Instant.ofEpochSecond(1_700_000_000)
    private fun id(genre: Genre, octet: Int) = Identifiant(genre, ByteArray(16) { octet.toByte() })

    private fun avecCompte(bloc: suspend (AnnuaireSimule) -> Unit) = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        annuaire.ouvrirCompte(CleLogicielle())
        bloc(annuaire)
    }

    /** Un signataire qui présente une clé et signe avec une AUTRE : sa preuve ne vérifie pas, et le banc doit le dire. */
    private class Usurpateur : Signataire {
        private val presentee = CleLogicielle()
        private val signe = CleLogicielle()
        override val clePublique get() = presentee.clePublique
        override suspend fun signer(message: ByteArray) = signe.signerSync(message)
    }

    /** Un signataire dont le porteur ne confirme jamais. */
    private class Refus : Signataire {
        private val cle = CleLogicielle()
        override val clePublique get() = cle.clePublique
        override suspend fun signer(message: ByteArray): ByteArray = throw NonConfirmeException()
    }

    @Test
    fun ouvrirUnCompteExigeUnePreuveSousLaClePresentee(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        assertThrows(ErreurAnnuaire.PreuveInvalide::class.java) { runBlocking { annuaire.ouvrirCompte(Usurpateur()) } }
        assertThrows(ErreurAnnuaire.NonConfirme::class.java) { runBlocking { annuaire.ouvrirCompte(Refus()) } }
        val compte = annuaire.ouvrirCompte(CleLogicielle())
        assertEquals(Genre.UTILISATEUR, compte.identifiant.genre)
        // Une seconde ouverture rend le même compte, sans redemander de preuve.
        assertEquals(compte, annuaire.ouvrirCompte(Refus()))
    }

    @Test
    fun unAppareilNeSeRevoquePasLuiMeme() = avecCompte { annuaire ->
        val moi = annuaire.appareils().first { it.estCeluiCi }
        assertThrows(ErreurAnnuaire.Interdit::class.java) { runBlocking { annuaire.revoquerAppareil(moi.id) } }
    }

    @Test
    fun unAppareilRevoqueResteMarque() = avecCompte { annuaire ->
        val autre = Appareil(id(Genre.APPAREIL, 7), "autre", Appareil.Biometrie.EMPREINTE, instant)
        annuaire.poser(autre)
        annuaire.revoquerAppareil(autre.id)
        val liste = annuaire.appareils()
        assertEquals(2, liste.size)
        assertTrue(liste.first { it.id == autre.id }.estRevoque)
    }

    @Test
    fun unAliasPrisRendConflit() = avecCompte { annuaire ->
        annuaire.inscrireAutreCompte(id(Genre.UTILISATEUR, 9), "vero")
        assertThrows(ErreurAnnuaire.AliasPris::class.java) { runBlocking { annuaire.definirAlias("VERO") } }
        annuaire.definirAlias("thierry")
        assertEquals("thierry", annuaire.compte()?.alias)
        annuaire.definirAlias(null)
        assertNull(annuaire.compte()?.alias)
    }

    @Test
    fun declarerRendUnCodeEtPasDeCle() = avecCompte { annuaire ->
        val machine = annuaire.declarerMachine("grenier", setOf(Capacite.ANNONCE))
        val cle = machine.cle as Machine.Cle.Attendue
        assertEquals(10, cle.code.symboles.length)
        assertTrue(cle.code.estValide(instant.plusSeconds(599)))
        assertTrue(!cle.code.estValide(instant.plusSeconds(600)))
    }

    @Test
    fun leCodePrecedentMeurtALemissionDuSuivant() = avecCompte { annuaire ->
        val machine = annuaire.declarerMachine("grenier", emptySet())
        val second = annuaire.emettreCode(machine.id)
        assertEquals(Machine.Cle.Attendue(second), annuaire.machines().first().cle)
    }

    @Test
    fun unPatchVideEstRefuse() = avecCompte { annuaire ->
        val machine = annuaire.declarerMachine("grenier", emptySet())
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) { runBlocking { annuaire.modifierMachine(machine.id) } }
        val renommee = annuaire.modifierMachine(machine.id, nom = "cave")
        assertEquals("cave", renommee.nom)
        assertTrue(renommee.capacites.isEmpty())
    }

    @Test
    fun retirerLannonceFaitTomberLesBaux() = avecCompte { annuaire ->
        val machine = Machine(id(Genre.MACHINE, 3), "nas", setOf(Capacite.ANNONCE, Capacite.LECTURE), Machine.Cle.Enrolee(instant))
        annuaire.poser(machine)
        val point = PointEcoute(PointEcoute.Protocole.TCP, 445)
        annuaire.annoncer(machine.id, Service(id(Genre.SERVICE, 4), "partage", listOf(point), Service.Etat.Annonce(instant)))
        val apres = annuaire.modifierMachine(machine.id, capacites = setOf(Capacite.LECTURE))
        assertEquals(Service.Etat.Parti(volontaire = false, le = instant), apres.services[0].etat)
    }

    @Test
    fun uneAnnonceDuMemeNomRemplaceLaPrecedente() = avecCompte { annuaire ->
        val machine = Machine(id(Genre.MACHINE, 3), "nas", setOf(Capacite.ANNONCE), Machine.Cle.Enrolee(instant))
        annuaire.poser(machine)
        fun annonce(port: Int) = Service(id(Genre.SERVICE, 4), "partage", listOf(PointEcoute(PointEcoute.Protocole.TCP, port)), Service.Etat.Annonce(instant))
        annuaire.annoncer(machine.id, annonce(445))
        annuaire.annoncer(machine.id, annonce(4_450))
        val relue = annuaire.machines().first()
        assertEquals(1, relue.services.size)
        assertEquals(4_450, relue.services[0].points[0].port)
    }

    @Test
    fun accorderExigeQueLeBeneficiaireExiste() = avecCompte { annuaire ->
        val inconnu = id(Genre.UTILISATEUR, 0xAB)
        assertThrows(ErreurAnnuaire.Introuvable::class.java) {
            runBlocking { annuaire.accorder(inconnu, Autorisation.Portee.Tout, "") }
        }
        annuaire.inscrireAutreCompte(inconnu, null)
        val arete = annuaire.accorder(inconnu, Autorisation.Portee.Tout, "ami")
        annuaire.revoquerAutorisation(arete.id)
        val liste = annuaire.autorisations()
        assertEquals(1, liste.size)
        assertTrue(liste[0].estRevoquee)
    }

    @Test
    fun revoquerUneAutorisationRecueRendLeMeme404() = avecCompte { annuaire ->
        val moi = annuaire.compte()!!.identifiant
        val recue = Autorisation(id(Genre.AUTORISATION, 0xEF), id(Genre.UTILISATEUR, 0xCD), moi, Autorisation.Portee.Tout, "", instant)
        annuaire.recevoir(recue)
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.revoquerAutorisation(recue.id) } }
    }
}
