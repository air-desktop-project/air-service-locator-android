package org.airdesktop.servicelocator.modele

import java.time.Duration
import java.time.Instant

/**
 * Le code qu'un administrateur recopie du téléphone vers un terminal :
 * `asl enrole 4K9M2-P7R1T`.
 *
 * Dix symboles de Crockford — cinquante bits — à usage unique, valables dix
 * minutes (`docs/modele.md` §2.3). Il est groupé pour l'œil, cinq par cinq :
 * c'est un humain qui le tape, et chaque symbole de trop est une occasion de
 * se tromper.
 *
 * **C'est un secret partagé, et il faut le dire** : ce qui le rend acceptable
 * est qu'il n'authentifie rien sur la durée. Une fois, quelques minutes, une
 * seule opération — lier une clé.
 */
data class CodeEnrolement(
    /** Les dix symboles, en majuscules canoniques. */
    val symboles: String,
    val expireLe: Instant,
) {
    /** Groupé pour l'œil : `4K9M2-P7R1T`. */
    val texteGroupe: String get() = symboles.take(5) + "-" + symboles.drop(5)

    /** La commande à taper sur la machine. */
    val commande: String get() = "asl enrole $texteGroupe"

    fun estValide(instant: Instant): Boolean = instant.isBefore(expireLe)

    /** Le temps qu'il reste, jamais négatif. */
    fun reste(instant: Instant): Duration = Duration.between(instant, expireLe).let { if (it.isNegative) Duration.ZERO else it }

    companion object {
        const val NOMBRE_SYMBOLES = 10
        val VALIDITE: Duration = Duration.ofSeconds(600)

        /** Depuis huit octets d'aléa, comme `asl_cle::CodeEnrolement::depuis_entropie`. */
        fun depuisEntropie(entropie: ByteArray, emisLe: Instant): CodeEnrolement {
            require(entropie.size == 8)
            var valeur = entropie.fold(0L) { acc, o -> (acc shl 8) or (o.toLong() and 0xFF) } ushr 14
            val caracteres = CharArray(NOMBRE_SYMBOLES)
            for (place in NOMBRE_SYMBOLES - 1 downTo 0) {
                caracteres[place] = Identifiant.ALPHABET[(valeur and 31).toInt()]
                valeur = valeur ushr 5
            }
            return CodeEnrolement(String(caracteres), emisLe.plus(VALIDITE))
        }
    }
}
