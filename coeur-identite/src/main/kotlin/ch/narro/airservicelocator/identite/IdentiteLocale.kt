package ch.narro.airservicelocator.identite

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

/**
 * Ce que l'appareil sait confirmer.
 */
sealed interface EtatIdentite {
    val description: String

    /** Biométrie forte disponible et enrôlée. Le seul cas nominal. */
    data object Disponible : EtatIdentite {
        override val description = "Identité confirmable sur cet appareil."
    }

    /**
     * Le matériel existe, mais rien n'y est enrôlé. **Ce n'est PAS un refus
     * définitif** : l'utilisateur peut le corriger dans les réglages, et le lui
     * dire vaut mieux que l'exclure.
     */
    data object RienEnrole : EtatIdentite {
        override val description = "Aucune empreinte ni visage enrôlé sur cet appareil."
    }

    /** Le matériel est là mais indisponible pour l'instant — mise à jour en cours, par exemple. */
    data object Indisponible : EtatIdentite {
        override val description = "Biométrie momentanément indisponible."
    }

    /**
     * Aucune biométrie forte sur cet appareil. **C'est le cas qui exclut
     * l'application**, et il doit se dire clairement plutôt que d'échouer plus
     * tard.
     */
    data class Absente(val raison: String) : EtatIdentite {
        override val description = "Biométrie forte absente : $raison"
    }
}

/**
 * Ce que l'appareil peut confirmer de l'identité de son porteur, et rien de plus.
 *
 * # La contrainte du produit, et ce qu'elle veut RÉELLEMENT dire
 *
 * `air-service-locator` ne se déploie que sur des appareils capables de
 * confirmer localement l'identité de leur porteur.
 *
 * **Cette confirmation a lieu SUR L'APPAREIL, et son résultat n'en sort pas.**
 * Android ne rend jamais un gabarit facial ni une empreinte : ces données vivent
 * dans le TEE ou l'élément sécurisé, et aucune API ne les expose. Ce que cette
 * classe obtient est un verdict de disponibilité, et un verdict n'est pas une
 * preuve : un client modifié en renverrait un aussi.
 *
 * **Le serveur ne doit donc jamais croire cette classe.** Ce qui vaut preuve
 * auprès de l'annuaire est une SIGNATURE produite par une clé du Keystore
 * matériel, créée avec `setUserAuthenticationRequired(true)` : le système refuse
 * alors de s'en servir tant que le porteur n'a pas été reconnu. La confirmation
 * devient une condition d'usage de la clé, vérifiée par le matériel, plutôt
 * qu'un résultat que le code transporte.
 *
 * Ce fichier ne fait que la PREMIÈRE moitié : constater ce dont l'appareil est
 * capable. La seconde — la clé, son contrôle d'accès, la signature, et son
 * attestation — reste à écrire, et sa spécification est dans `docs/protocole.md`
 * du dépôt serveur.
 *
 * # `BIOMETRIC_STRONG`, et pas autre chose
 *
 * Android distingue le « fort » du « faible ». Seule la classe FORTE peut
 * déverrouiller une clé du Keystore ; la faible — certaines reconnaissances
 * faciales par simple caméra — rend un booléen et rien d'autre. Comme tout
 * l'édifice repose sur une clé matérielle, accepter le faible ferait croire à
 * une garantie qui n'existerait pas.
 *
 * Le repli par code de verrouillage (`DEVICE_CREDENTIAL`) est délibérément
 * ABSENT : l'énoncé du produit demande une reconnaissance faciale ou une
 * empreinte, pas un code.
 */
class IdentiteLocale(private val contexte: Context) {

    fun etat(): EtatIdentite =
        when (BiometricManager.from(contexte).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                EtatIdentite.Disponible

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                EtatIdentite.RienEnrole

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                EtatIdentite.Indisponible

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                EtatIdentite.Absente("aucun matériel biométrique")

            // Le matériel est là, mais une mise à jour de sécurité manque et le
            // système ne lui fait plus confiance. C'est un cas RÉEL sur des
            // appareils anciens, et il ne se corrige pas depuis l'application.
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                EtatIdentite.Absente("mise à jour de sécurité requise")

            // `BIOMETRIC_STATUS_UNKNOWN` et `BIOMETRIC_ERROR_UNSUPPORTED` : le
            // système ne conclut pas. On ne conclut pas non plus à sa place —
            // mais on ne peut pas non plus laisser passer.
            else ->
                EtatIdentite.Absente("verdict indéterminé du système")
        }
}
