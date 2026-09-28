package org.airdesktop.servicelocator.reseau

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Les `external fun` de [Natif] face aux symboles que l'objet JNI du client 0.20.0 (`5ae7c10`) exporte.
 *
 * Un symbole que la `.so` n'exporte plus ne casse rien à la compilation : il casse au premier appel, sur le
 * téléphone, en `UnsatisfiedLinkError`. Cet essai le dit sur la JVM. La classe est lue SANS être initialisée —
 * son `init` chargerait la bibliothèque, absente ici.
 */
class NatifEssais {
    /** `Java_org_airdesktop_servicelocator_reseau_Natif_*` dans `crates/asl-client-android/src/lib.rs` à `5ae7c10`. */
    private val exportes = setOf(
        "annuaireIdentifie", "cle", "connecter", "creerCompte", "deconnecter", "defi", "dernierCode", "diagnostic",
        "distante", "fauteTexte", "identifiant", "identite", "liaison", "libere", "messagePourAttestation",
        "messagePourAttestationDeCle", "neuf", "nouvelle", "nouvellesOuvrir", "nouvellesRecues", "rejoindreAtteste",
        "requete",
    )

    private val declares: Set<String> by lazy {
        val classe = Class.forName("org.airdesktop.servicelocator.reseau.Natif", false, javaClass.classLoader)
        classe.declaredMethods.filter { Modifier.isNative(it.modifiers) }.map { it.name }.toSet()
    }

    @Test
    fun chaqueSymboleDeclareEstExportePar0200() {
        assertTrue(declares.isNotEmpty())
        assertTrue("absents de la .so : ${declares - exportes}", exportes.containsAll(declares))
    }

    @Test
    fun lAnnuaireParNomNExistePlus() {
        // `Natif_annuaire` (un nom résolu par le téléphone) est retiré du client 0.20.0 : seule l'identité reste.
        assertFalse("annuaire" in declares)
        assertTrue("annuaireIdentifie" in declares)
    }
}
