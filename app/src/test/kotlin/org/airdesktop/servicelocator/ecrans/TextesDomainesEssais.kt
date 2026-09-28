package org.airdesktop.servicelocator.ecrans

import org.airdesktop.servicelocator.modele.EtatDInscription
import org.airdesktop.servicelocator.modele.Genre
import org.airdesktop.servicelocator.modele.Identifiant
import org.airdesktop.servicelocator.modele.Inscription
import org.airdesktop.servicelocator.reseau.ErreurAnnuaire
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les mots des domaines, à la lettre ceux d'iOS/macOS (`TextesDomaines`) : un écart ici est un écart entre les apps. */
class TextesDomainesEssais {
    private val n = Identifiant(Genre.ANNUAIRE, ByteArray(16) { 3 })

    @Test
    fun laCommandeDInscriptionNommeLaRacineEtLaisseLeResteLitteral() {
        assertEquals(
            "asl-server --register 4K9M2-P7R1T --directory [2001:41d0:20a:900::1dd4]:6630=n-0PWT8HZD80QMSPPDZ5CQXXYHQC --identity-key <clé>",
            TextesDomaines.commande("4K9M2-P7R1T", "[2001:41d0:20a:900::1dd4]:6630=n-0PWT8HZD80QMSPPDZ5CQXXYHQC"),
        )
    }

    @Test
    fun lesEtatsDInscriptionEtUnEtatInconnuTelQuel() {
        fun etat(e: EtatDInscription, mot: String) = TextesDomaines.etat(Inscription(e, mot, null, null, null, "a:1", null))
        assertEquals("code pas encore présenté", etat(EtatDInscription.Attendue, "attendue"))
        assertEquals("en attente de la décision des racines", etat(EtatDInscription.EnAttente, "en attente"))
        assertEquals("acceptée", etat(EtatDInscription.Acceptee, "acceptée"))
        assertEquals("refusée", etat(EtatDInscription.Refusee, "refusée"))
        assertEquals("retirée", etat(EtatDInscription.Retiree, "retirée"))
        assertEquals("suspendue", etat(EtatDInscription.Inconnu, "suspendue"))
    }

    @Test
    fun lesPhrasesQuiDependentDUneValeur() {
        assertEquals("Hébergé par : l'annuaire ${n.texte}", TextesDomaines.hebergeAnnuaire(n))
        assertEquals(
            "L'annuaire n-X (speedy:6630), du compte u-Y, servira les domaines qu'on lui confiera.",
            TextesDomaines.confirmerAcceptation("n-X", "speedy:6630", "u-Y"),
        )
    }

    @Test
    fun lesRefusDisentLesMemesMotsQuIOS() {
        assertEquals("C'est votre dernier domaine : un compte en garde toujours un.", ErreurAnnuaire.DernierDomaine.message)
        assertEquals("Vous n'avez pas le droit de ranger une machine dans ce domaine.", ErreurAnnuaire.SansDroitDeRattacher.message)
        assertEquals("Un second membre est déjà déclaré, en attente ou accepté.", ErreurAnnuaire.PaireComplete.message)
        assertEquals("Cette inscription est déjà refusée ou retirée : elle ne peut plus être acceptée.", ErreurAnnuaire.InscriptionTranchee.message)
    }
}
