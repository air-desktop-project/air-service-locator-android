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
    @Test
    fun leBancEnroleUnAppareilDePlusEtLeLaisseRejoindre(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        val nouveau = CleLogicielle()
        // Sans compte, rien à enrôler.
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.enrolerAppareil(nouveau.clePublique) } }
        val compte = annuaire.ouvrirCompte(CleLogicielle())
        val enrole = annuaire.enrolerAppareil(nouveau.clePublique)
        assertEquals(Genre.APPAREIL, enrole.id.genre)
        assertTrue(!enrole.estCeluiCi)
        assertEquals(2, annuaire.appareils().size)
        // Deux fois la même clé, non.
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) { runBlocking { annuaire.enrolerAppareil(nouveau.clePublique) } }

        // La clé à montrer est celle du signataire, telle quelle ; l'annuler ne change rien ici.
        assertTrue(annuaire.clePourRejoindre { nouveau }.contentEquals(nouveau.clePublique))
        annuaire.annulerRejoindre()

        // Rejoindre : le bon appareil sous la bonne clé, et la preuve tient. Sans chaîne, il entre `aucune`.
        assertEquals(compte, annuaire.rejoindre(compte.identifiant, enrole.id, nouveau))
        val appareils = annuaire.appareils()
        assertTrue(appareils.first { it.id == enrole.id }.estCeluiCi)
        assertEquals(1, appareils.count { it.estCeluiCi })
        assertEquals(Appareil.Attestation.AUCUNE, appareils.first { it.id == enrole.id }.attestation)

        // Une autre clé sous cet identifiant : introuvable, comme un 404 qui ne dit pas pourquoi.
        assertThrows(ErreurAnnuaire.Introuvable::class.java) {
            runBlocking { annuaire.rejoindre(compte.identifiant, enrole.id, CleLogicielle()) }
        }
    }

    /** Une clé qui porte une chaîne entre `android` : c'est ce que `POST /v1/attestation` fait d'un appareil qui rejoint. */
    @Test
    fun unAppareilQuiRejointAvecSaChaineEntreAtteste(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        val compte = annuaire.ouvrirCompte(CleLogicielle())
        val attestee = object : Signataire by CleLogicielle() {
            override val attestation: ByteArray get() = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)
        }
        val enrole = annuaire.enrolerAppareil(attestee.clePublique)
        assertEquals(compte, annuaire.rejoindre(compte.identifiant, enrole.id, attestee))
        assertEquals(Appareil.Attestation.ANDROID, annuaire.appareils().first { it.id == enrole.id }.attestation)
    }

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

    /** `PUT /v1/appareils/{a}/description` : pour soi seulement, le modèle aux règles du nom de machine. */
    @Test
    fun unAppareilSeDecrit() = avecCompte { annuaire ->
        assertThrows(ErreurAnnuaire.Introuvable::class.java) {
            runBlocking { AnnuaireSimule { instant }.decrire(Appareil.Description(Appareil.Plateforme.ANDROID, "Fairphone FP5")) }
        }
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) {
            runBlocking { annuaire.decrire(Appareil.Description(Appareil.Plateforme.ANDROID, "")) }
        }
        assertThrows(ErreurAnnuaire.RequeteInvalide::class.java) {
            runBlocking { annuaire.decrire(Appareil.Description(Appareil.Plateforme.ANDROID, "é".repeat(33))) }
        }
        annuaire.decrire(Appareil.Description(Appareil.Plateforme.ANDROID, "Fairphone FP5"))
        val moi = annuaire.appareils().first { it.estCeluiCi }
        assertEquals(Appareil.Description(Appareil.Plateforme.ANDROID, "Fairphone FP5"), moi.description)
        assertEquals("Fairphone FP5", moi.titre)
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

    /** `DELETE /v1/compte` : tout part dans une transaction, la clé de l'appareil enrôlé ne rejoint plus rien, un second appel rend `401`. */
    @Test
    fun effacerLeCompteFaitToutPartirEtRefuseLaCleEnsuite(): Unit = runBlocking {
        val annuaire = AnnuaireSimule { instant }
        // Sans compte, c'est le `401` du fil : rien à effacer sous cette clé.
        assertThrows(ErreurAnnuaire.NonReconnu::class.java) { runBlocking { annuaire.effacerCompte() } }
        val compte = annuaire.ouvrirCompte(CleLogicielle())
        val autre = CleLogicielle()
        val enrole = annuaire.enrolerAppareil(autre.clePublique)
        annuaire.declarerMachine("grenier", setOf(Capacite.ANNONCE))
        annuaire.definirAlias("thierry")
        val ami = id(Genre.UTILISATEUR, 0xAB)
        annuaire.inscrireAutreCompte(ami, "ami")
        annuaire.accorder(ami, Autorisation.Portee.Tout, "ami")
        annuaire.recevoir(Autorisation(id(Genre.AUTORISATION, 0xEF), ami, compte.identifiant, Autorisation.Portee.Tout, "", instant))

        annuaire.effacerCompte()

        assertNull(annuaire.compte())
        assertTrue(annuaire.appareils().isEmpty())
        assertTrue(annuaire.machines().isEmpty())
        assertTrue(annuaire.autorisations().isEmpty())
        // L'alias est libéré, l'identifiant ne se résout plus ; l'ami, lui, existe toujours.
        assertNull(annuaire.identifiantPourAlias("thierry"))
        assertTrue(!annuaire.utilisateurExiste(compte.identifiant))
        assertTrue(annuaire.utilisateurExiste(ami))
        // La clé de l'appareil enrôlé ne rejoint plus rien, et un second effacement est refusé comme sur le fil.
        assertThrows(ErreurAnnuaire.Introuvable::class.java) { runBlocking { annuaire.rejoindre(compte.identifiant, enrole.id, autre) } }
        assertThrows(ErreurAnnuaire.NonReconnu::class.java) { runBlocking { annuaire.effacerCompte() } }
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
        val code = (machine.cle as Machine.Cle.Attendue).code ?: error("une machine déclarée attend son code")
        assertEquals(10, code.symboles.length)
        assertTrue(code.estValide(instant.plusSeconds(599)))
        assertTrue(!code.estValide(instant.plusSeconds(600)))
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
