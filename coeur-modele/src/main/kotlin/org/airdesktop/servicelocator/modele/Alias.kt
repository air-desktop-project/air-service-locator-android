package org.airdesktop.servicelocator.modele

import java.text.Normalizer

/**
 * Les alias — de machine, de compte — tels que l'annuaire les range depuis 0.26.0
 * (`docs/modele.md` §2.1 et §2.3, décisions 45 à 47).
 *
 * # UNE FORME, DEUX BORNES
 *
 * Un alias est de l'UTF-8 **en forme normalisée NFC**, **sensible à la casse** : « Maison »
 * et « maison » sont deux alias, mais « é » saisi composé ou décomposé en est un seul — ce
 * n'est pas une question de casse, c'est une question d'écriture. Sont refusés les contrôles
 * C0, DEL et C1, les forceurs de sens d'écriture, la marque d'ordre des octets, et `"` et `\`,
 * que la grammaire JSON de l'API refuse. Les bornes se mesurent en octets APRÈS NFC, comme le
 * serveur les mesure : c'est ce qui est rangé qui compte, pas ce qui a été tapé.
 *
 * L'application ne décide rien de plus que le serveur : ces fonctions disent d'avance ce qu'il
 * refuserait, pour que le bouton se grise au lieu qu'un `400` arrive après le geste.
 */
object Alias {
    /** Un alias de machine, au plus : la longueur d'un nom de domaine complet (RFC 1035). */
    const val MACHINE_OCTETS_MAX = 253

    /** Un alias de compte, au moins et au plus. */
    const val COMPTE_OCTETS_MIN = 3
    const val COMPTE_OCTETS_MAX = 32

    /**
     * Ce qu'on peut SAISIR, avant NFC, comme l'annuaire l'admet brut (`ALIAS_DE_COMPTE_BRUT_MAX`,
     * `ALIAS_DE_MACHINE_BRUT_MAX`) : une forme décomposée peut être plus longue et se recomposer sous la borne, on la
     * laisse entrer jusque-là — au-delà, l'annuaire la refuse avant même de la normaliser.
     */
    const val COMPTE_SAISIE_OCTETS_MAX = 255
    const val MACHINE_SAISIE_OCTETS_MAX = 480

    /** La version de l'annuaire qui range l'alias d'une machine : en dessous, le champ ne s'affiche pas. */
    const val VERSION_ALIAS_DE_MACHINE = "0.26.0"

    /** Le texte en NFC — la forme que l'annuaire range, et donc celle qu'on lui envoie. */
    fun nfc(texte: String): String = Normalizer.normalize(texte, Normalizer.Form.NFC)

    /**
     * L'alias de machine prêt à envoyer, ou `null` s'il serait refusé : non vide, au plus
     * 253 octets après NFC, aucun caractère refusé. **Indépendant du nom et du domaine** : il
     * peut contenir tout autre chose qu'un « nom.domaine », et c'est voulu. **Non unique.**
     */
    fun pourMachine(texte: String): String? {
        if (texte.toByteArray(Charsets.UTF_8).size > MACHINE_SAISIE_OCTETS_MAX) return null
        val forme = nfc(texte)
        val octets = forme.toByteArray(Charsets.UTF_8).size
        return forme.takeIf { octets in 1..MACHINE_OCTETS_MAX && admis(it) }
    }

    /**
     * L'alias de compte prêt à envoyer, ou `null` : trois à trente-deux octets après NFC,
     * aucun caractère refusé, et **un deuxième caractère qui n'est pas un tiret** — dans le
     * champ où l'on tape soit un identifiant, soit un alias, « u-… » doit rester un identifiant.
     * **Unique** sur l'annuaire : c'est lui qui le dit, par un `409`.
     */
    fun pourCompte(texte: String): String? {
        if (texte.toByteArray(Charsets.UTF_8).size > COMPTE_SAISIE_OCTETS_MAX) return null
        val forme = nfc(texte)
        val octets = forme.toByteArray(Charsets.UTF_8).size
        if (octets !in COMPTE_OCTETS_MIN..COMPTE_OCTETS_MAX || !admis(forme)) return null
        val deuxieme = forme.codePoints().skip(1).findFirst()
        return forme.takeIf { !deuxieme.isPresent || deuxieme.asInt != '-'.code }
    }

    /**
     * Cet annuaire range-t-il l'alias d'une machine ? Vrai pour une version semver ≥ 0.26.0 ; **faux pour une version
     * illisible** (« banc en mémoire », absente) : dans le doute, on ne propose pas un champ que l'annuaire refuserait.
     */
    fun aliasDeMachineAdmis(version: String?): Boolean {
        val parties = version?.split('.')?.takeIf { it.size == 3 }?.map { it.toIntOrNull() ?: return false } ?: return false
        val (majeur, mineur, _) = parties
        return majeur > 0 || mineur >= 26
    }

    /** Aucun caractère que l'annuaire refuse. */
    private fun admis(texte: String): Boolean = texte.codePoints().noneMatch { point ->
        point == '"'.code || point == '\\'.code ||
            point < 0x20 || point in 0x7F..0x9F ||   // C0, DEL, C1
            point == 0xFEFF ||                       // marque d'ordre
            point in 0x202A..0x202E ||               // forceurs de sens
            point in 0x2066..0x2069
    }
}
