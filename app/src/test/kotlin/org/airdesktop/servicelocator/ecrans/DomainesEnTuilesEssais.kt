package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.Capacite
import org.airdesktop.servicelocator.modele.CodeEnrolement
import org.airdesktop.servicelocator.modele.DetailDuDomaine
import org.airdesktop.servicelocator.modele.Domaine
import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.modele.Machine
import org.airdesktop.servicelocator.modele.MachineDuDomaine
import org.airdesktop.servicelocator.composants.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ce que les pages des domaines, la fiche machine et le compte montrent et permettent — décidé comme le Mac
 * (`DomainesFenetreVue`, `MachineFenetreVue`, `CompteFenetreVue`), avec ses mots à la lettre.
 */
class DomainesEnTuilesEssais {
    private val moi = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 1 })
    private val autre = Identifiant(Genre.UTILISATEUR, ByteArray(16) { 2 })
    private val d = Identifiant(Genre.DOMAINE, ByteArray(16) { 3 })
    private val speedy = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 7 })
    private fun m(o: Int) = Identifiant(Genre.MACHINE, ByteArray(16) { o.toByte() })

    private fun domaine(proprietaire: Identifiant, vararg droits: String, hebergePar: Identifiant? = null) =
        Domaine(d, proprietaire, null, hebergePar, droits.toSet())

    // ── Les services des machines d'autrui (0.40.0, décision 104) ──

    @Test
    fun lesServicesDAutruiSeLisentDesQueLeDomaineDonneVoir() {
        val sienne = MachineDuDomaine(m(2), autre, "nas", null)
        assertTrue(servicesLisibles(sienne, domaine(autre, Domaine.VOIR), moi))
        assertTrue(servicesLisibles(sienne, domaine(autre, Domaine.LOCALISER), moi))
        assertTrue(servicesLisibles(sienne, domaine(autre, Domaine.ADMINISTRER), moi))
        assertFalse(servicesLisibles(sienne, domaine(autre, Domaine.RATTACHER), moi))
        assertFalse(servicesLisibles(sienne, domaine(autre), moi))
        // Les miennes, toujours — même rangées là où je ne tiens que `rattacher`.
        assertTrue(servicesLisibles(MachineDuDomaine(m(1), moi, "speedy", null), domaine(autre, Domaine.RATTACHER), moi))
    }

    @Test
    fun rangerChezAutruiLeDitAvantDeConfirmerEtChezSoiRien() {
        val attendu = "Qui voit ce domaine verra les services de cette machine ; qui y localise les joindra."
        assertEquals(attendu, TextesPageDomaines.rangerChezAutrui)
        assertEquals(attendu, avertissementDeRangement(domaine(autre, Domaine.RATTACHER), moi))
        assertEquals(null, avertissementDeRangement(domaine(moi, Domaine.RATTACHER), moi))
        assertEquals(null, avertissementDeRangement(null, moi))
    }

    // ── Le badge et les gestes d'un domaine ──

    @Test
    fun leBadgeDitLeRole() {
        assertEquals(RoleDuDomaine(Role.Proprietaire, "Propriétaire"), RoleDuDomaine.de(domaine(moi, Domaine.ADMINISTRER), moi))
        assertEquals(RoleDuDomaine(Role.Administrateur, "Administrer"), RoleDuDomaine.de(domaine(autre, Domaine.ADMINISTRER), moi))
        assertEquals(RoleDuDomaine(Role.Droits, "voir · localiser"), RoleDuDomaine.de(domaine(autre, Domaine.VOIR, Domaine.LOCALISER), moi))
    }

    /** Ce que l'annuaire rend au propriétaire d'un domaine ordinaire, et à son groupe d'administrateurs (`protocole.md`). */
    private val quatre = arrayOf(Domaine.ADMINISTRER, Domaine.RATTACHER, Domaine.VOIR, Domaine.LOCALISER)
    private val administrateurs = arrayOf(Domaine.ADMINISTRER, Domaine.RATTACHER, Domaine.VOIR)

    @Test
    fun aMoiTousLesGestes() {
        assertEquals(GestesDuDomaine(renommer = true, changerHebergeur = true, ranger = true, supprimer = true), GestesDuDomaine.de(domaine(moi, *quatre), moi))
    }

    @Test
    fun administrerDonneDeRenommerEtDeRangerPasDeSupprimerNiDeConfier() {
        assertEquals(GestesDuDomaine(renommer = true, changerHebergeur = false, ranger = true, supprimer = false), GestesDuDomaine.de(domaine(autre, *administrateurs), moi))
    }

    @Test
    fun leDomaineRacineNeSeProposePasAuRangementMemeASonProprietaire() {
        // R (décision 43) : son propriétaire calculé n'y tient que `administrer`, et l'annuaire refuse d'y ranger (404).
        val racine = GestesDuDomaine.de(domaine(moi, Domaine.ADMINISTRER), moi)
        assertFalse(racine.ranger)
        assertFalse(racine.changerHebergeur)
        assertTrue(racine.renommer)
        // Posséder un domaine sans y tenir `rattacher` ne suffit pas : seule la liste des droits décide.
        assertFalse(GestesDuDomaine.de(domaine(moi, Domaine.VOIR), moi).ranger)
    }

    @Test
    fun leDomaineRacineNeSeConfieJamaisMemeAvecRattacher() {
        // R servi par un annuaire 0.39.0 : les quatre droits, comme un domaine ordinaire ; seule la sorte le distingue.
        val racine = Domaine(d, moi, null, null, quatre.toSet(), racine = true)
        val gestes = GestesDuDomaine.de(racine, moi)
        assertFalse(gestes.changerHebergeur)
        // Le rangement reste à `rattacher` : R reçoit des machines dès que l'annuaire le permet.
        assertTrue(gestes.ranger)
        assertTrue(gestes.renommer)
        // Un domaine ordinaire, avec `rattacher`, se confie toujours.
        assertTrue(GestesDuDomaine.de(domaine(moi, *quatre), moi).changerHebergeur)
        assertEquals("Domaine racine", TextesPageDomaines.domaineRacine)
    }

    @Test
    fun leDomaineRacineNeSeSupprimePasMemeASonProprietaire() {
        // R est calculé : son propriétaire ne se voit pas offrir « Supprimer le domaine… », même avec les quatre droits.
        assertFalse(GestesDuDomaine.de(Domaine(d, moi, null, null, quatre.toSet(), racine = true), moi).supprimer)
        // Un domaine ordinaire à moi se supprime toujours (le dernier, c'est l'annuaire qui le refuse).
        assertTrue(GestesDuDomaine.de(domaine(moi, *quatre), moi).supprimer)
    }

    @Test
    fun rattacherDonneDeRangerSeulementEtVoirRien() {
        assertEquals(GestesDuDomaine(renommer = false, changerHebergeur = false, ranger = true, supprimer = false), GestesDuDomaine.de(domaine(autre, Domaine.RATTACHER), moi))
        assertEquals(GestesDuDomaine(renommer = false, changerHebergeur = false, ranger = false, supprimer = false), GestesDuDomaine.de(domaine(autre, Domaine.VOIR), moi))
        // Sans compte lu, rien n'est « à moi ».
        assertFalse(GestesDuDomaine.de(domaine(moi, Domaine.VOIR), null).supprimer)
    }

    // ── Les machines rangées ──

    @Test
    fun onNeRetireQueSesMachinesEtLaLigneDitAQuiElleEst() {
        val mienne = MachineDuDomaine(m(1), moi, "speedy", null)
        val sienne = MachineDuDomaine(m(2), autre, null, null)
        assertTrue(retirableDuDomaine(mienne, moi))
        assertFalse(retirableDuDomaine(sienne, moi))
        assertFalse(retirableDuDomaine(mienne, null))
        assertEquals("${m(1).texte} · à vous", sousTitreDeMachineRangee(mienne, moi))
        assertEquals("${m(2).texte} · ${autre.texte}", sousTitreDeMachineRangee(sienne, moi))
    }

    @Test
    fun onNeRangeIciQueCeQuiNYEstPas() {
        val detail = DetailDuDomaine(domaine(moi, Domaine.ADMINISTRER), listOf(MachineDuDomaine(m(1), moi, "speedy", null)))
        val miennes = listOf(1, 2, 3).map { Machine(m(it), "m$it", setOf(Capacite.ANNONCE), Machine.Cle.Enrolee()) }
        assertEquals(listOf(m(2), m(3)), machinesARanger(miennes, detail).map { it.id })
    }

    // ── L'hébergement ──

    private fun inscription(etat: EtatDInscription, membre: Identifiant?, annuaire: Identifiant?, adresse: String) =
        Inscription(etat, etat.mot, membre, annuaire, null, adresse, null)

    @Test
    fun onNeConfieQuAUnTitulaireAccepte() {
        val second = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 8 })
        val locaux = listOf(
            inscription(EtatDInscription.Acceptee, speedy, speedy, "[2001:db8::1]:6630"),
            inscription(EtatDInscription.Acceptee, second, speedy, "[2001:db8::2]:6630"),
            inscription(EtatDInscription.EnAttente, second, second, "[2001:db8::3]:6630"),
            inscription(EtatDInscription.Attendue, null, null, "[2001:db8::4]:6630"),
        )
        assertEquals(listOf(speedy), titulairesAcceptes(locaux).map { it.annuaire })
        assertEquals("Mon annuaire local — ${speedy.texte} · [2001:db8::1]:6630", TextesPageDomaines.optionDAnnuaire(speedy, "[2001:db8::1]:6630"))
    }

    @Test
    fun hebergeParDitQuiPuisSesAdresses() {
        val racines = Hebergement("", listOf(Hebergement.Serveur("nitrogen.air-desktop.org", listOf("[2001:41d0:20a:900::1dd4]:6630", "178.32.16.250:6630"))))
        assertEquals(
            "Les racines" to listOf("nitrogen.air-desktop.org — [2001:41d0:20a:900::1dd4]:6630 · 178.32.16.250:6630"),
            lignesDHebergement(domaine(moi), racines, emptyList()),
        )
        val local = Hebergement("", listOf(Hebergement.Serveur(speedy.abrege, listOf("[2001:db8::1]:6630"))))
        val mien = listOf(inscription(EtatDInscription.Acceptee, speedy, speedy, "[2001:db8::1]:6630"))
        assertEquals("Mon annuaire local" to listOf(speedy.texte, "[2001:db8::1]:6630"), lignesDHebergement(domaine(moi, hebergePar = speedy), local, mien))
        // Un annuaire local qui n'est pas à moi — un domaine partagé : on ne dit pas « mon ».
        assertEquals("L'annuaire local" to listOf(speedy.texte), lignesDHebergement(domaine(autre, hebergePar = speedy), Hebergement("", emptyList()), emptyList()))
    }

    @Test
    fun lesMotsDesDomainesALaLettre() {
        assertEquals("Ranger une machine ici…", TextesPageDomaines.rangerIci)
        assertEquals("Retirer du domaine…", TextesPageDomaines.retirerDuDomaine)
        assertEquals("Changer…", TextesPageDomaines.changer)
        assertEquals("Modifier…", TextesPageDomaines.modifier)
        assertEquals("tenus par vos groupes", TextesPageDomaines.tenusParVosGroupes)
        assertEquals("Qui sert « Maison » ?", TextesPageDomaines.hebergementTitre("Maison"))
        assertEquals("Retirer « speedy »", TextesPageDomaines.retirerLaMachine("speedy"))
        assertEquals(
            "Supprimer détache ses machines ; son alias et ses groupes disparaissent. Votre dernier domaine ne se supprime pas.",
            TextesPageDomaines.suppression,
        )
    }

    // ── La fiche machine ──

    @Test
    fun laCleSeDitCommeSurLeMac() {
        val le = Instant.parse("2026-09-20T10:00:00Z")
        val maintenant = Instant.parse("2026-09-28T10:00:00Z")
        assertEquals("enrôlée le ${Formats.jour(le)} — Ed25519, générée sur la machine", TextesMachine.cle(Machine.Cle.Enrolee(le), maintenant))
        assertEquals("enrôlée — Ed25519, générée sur la machine", TextesMachine.cle(Machine.Cle.Enrolee(), maintenant))
        assertEquals("attendue — pas de code en cours", TextesMachine.cle(Machine.Cle.Attendue(), maintenant))
        val code = CodeEnrolement("4K9M2P7R1T", maintenant.plusSeconds(300))
        assertEquals("attendue — un code est valable", TextesMachine.cle(Machine.Cle.Attendue(code), maintenant))
        assertEquals("attendue — le code a expiré", TextesMachine.cle(Machine.Cle.Attendue(code), maintenant.plusSeconds(301)))
        assertEquals("révoquée le ${Formats.jour(le)} — un nouveau code la ré-enrôle", TextesMachine.cle(Machine.Cle.Revoquee(le), maintenant))
    }

    @Test
    fun lesCapacitesEnToutesLettres() {
        assertEquals(
            listOf("annonce — ses daemons peuvent annoncer leurs ports", "lecture — elle peut demander où joindre un service"),
            TextesMachine.capacites(setOf(Capacite.LECTURE, Capacite.ANNONCE)),
        )
        assertTrue(TextesMachine.capacites(emptySet()).isEmpty())
    }

    @Test
    fun lesMotsDeLaFicheEtDuCompteALaLettre() {
        assertEquals("Révoquer la clé…", TextesMachine.revoquer)
        assertEquals("Révoquer la clé de « speedy » ?", TextesMachine.confirmerRevocation("speedy"))
        assertEquals("Choisir…", TextesCompte.choisir)
        assertEquals("aucun — sans alias, seul l'identifiant vous rend trouvable", TextesCompte.sansAlias)
        assertEquals("Effacer mon compte…", TextesCompte.effacer)
        assertEquals("Effacer ce compte ?", TextesCompte.confirmerEffacement)
    }
}
