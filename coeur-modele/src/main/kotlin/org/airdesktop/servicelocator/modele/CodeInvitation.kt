package org.airdesktop.servicelocator.modele

/**
 * Le code qu'un exploitant donne à qui il invite : `4K9M2-P7R1T`.
 *
 * **La même forme qu'un [CodeEnrolement]** — dix symboles de Crockford,
 * cinquante bits — et c'est voulu : un humain le recopie dans les mêmes
 * conditions, d'un message ou d'un bout de papier (`protocole.md` §2.2). Ce
 * qui diffère est ce qu'il ouvre : un code d'enrôlement lie une clé de machine
 * à un compte qui existe déjà, celui-ci ouvre un compte sur une racine qui
 * n'entre que sur invitation.
 *
 * **Ce type ne porte pas d'échéance**, là où [CodeEnrolement] en porte une.
 * L'application ne l'émet pas : elle le reçoit d'un humain, qui le tient de
 * l'exploitant. Elle ne sait donc pas quand il a été émis, et une échéance
 * qu'elle inventerait mentirait. C'est l'annuaire qui refuse un code expiré,
 * et il ne dit pas lequel des trois refus c'est (`protocole.md` §2.2).
 *
 * **C'est un secret partagé, et il faut le dire** : ce qui le rend acceptable
 * est qu'il n'authentifie rien sur la durée. Une fois, un jour au plus, une
 * seule opération — ouvrir un compte.
 */
@JvmInline
value class CodeInvitation private constructor(
    /** Les dix symboles, en majuscules canoniques, sans tiret. */
    val symboles: String,
) {
    /** Groupé pour l'œil, comme l'exploitant l'a écrit : `4K9M2-P7R1T`. */
    val texteGroupe: String get() = symboles.take(COUPURE) + "-" + symboles.drop(COUPURE)

    /**
     * Les dix octets qui voyagent dans la case d'attestation de
     * `POST /v1/comptes`, sous la plate-forme `3`.
     *
     * **Ce sont les symboles eux-mêmes**, en ASCII et sous leur forme
     * canonique : l'annuaire relit ce texte et en prend l'empreinte
     * (`protocole.md` §2.2). Le tiret d'affichage n'y entre pas — il ferait
     * onze octets, et l'annuaire refuse tout ce qui n'en fait pas dix.
     */
    val octets: ByteArray get() = symboles.toByteArray(Charsets.US_ASCII)

    companion object {
        const val NOMBRE_SYMBOLES = 10
        private const val COUPURE = 5

        /**
         * Lit un code tapé par un humain, ou rend `null` s'il n'en est pas un.
         *
         * **Tolérant à la saisie, strict sur ce qui part.** La casse est
         * indifférente, le tiret d'affichage est admis, les espaces autour
         * aussi, et les confusions de Crockford sont rattrapées — un `O` tapé
         * pour un zéro, un `I` ou un `L` pour un un ([Identifiant.valeur]).
         * Ce qui sort est toujours la forme canonique, la seule que l'annuaire
         * relise : à quoi bon faire échouer quelqu'un sur la forme d'une lettre
         * quand la seule question est de savoir si l'exploitant l'a invité.
         */
        fun analyser(texte: String): CodeInvitation? {
            val sansSeparateur = texte.trim().replace("-", "").replace(" ", "")
            if (sansSeparateur.length != NOMBRE_SYMBOLES) return null
            val canonique = StringBuilder(NOMBRE_SYMBOLES)
            for (c in sansSeparateur) {
                val valeur = Identifiant.valeur(c) ?: return null
                canonique.append(Identifiant.ALPHABET[valeur])
            }
            return CodeInvitation(canonique.toString())
        }
    }
}
