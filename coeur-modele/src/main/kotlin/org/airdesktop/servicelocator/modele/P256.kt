package org.airdesktop.servicelocator.modele

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve

/**
 * La forme sur le fil d'une clé et d'une signature d'appareil, et ce que le
 * serveur en fait — reproduit ici pour les essais et le banc
 * (`asl_cle::CleAppareil`).
 *
 * | Quoi | Octets | Forme |
 * |---|---|---|
 * | Clé publique | 33 | SEC1 compressé : `02` ou `03` ‖ x |
 * | Signature | 64 | `r ‖ s`, chacun sur trente-deux octets, gros-boutien |
 *
 * Java rend une clé en X.509 et une signature en DER, de longueur variable.
 * **Ce dépôt refuse les longueurs qui viennent du réseau** : tout est déplié
 * ici, d'un seul côté, et le serveur ne voit que des tailles fixes.
 */
object P256 {
    private val P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    private val A = P.subtract(BigInteger.valueOf(3))
    private val B = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
    private val N = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)
    private val G = ECPoint(
        BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16),
        BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16),
    )
    val PARAMETRES = ECParameterSpec(EllipticCurve(ECFieldFp(P), A, B), G, N, 1)

    /** La clé publique en SEC1 compressé : `02` si y est pair, `03` sinon, puis x sur trente-deux octets. */
    fun compresser(cle: ECPublicKey): ByteArray {
        val x = cle.w.affineX.toByteArray().let { it.copyOfRange(maxOf(0, it.size - 32), it.size) }
        val prefixe: Byte = if (cle.w.affineY.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefixe) + ByteArray(32 - x.size) + x
    }

    /**
     * Le point depuis sa forme compressée, ou `null` si les octets n'en font pas
     * un. **C'est une vérification réelle** : un préfixe qui n'est ni `02` ni
     * `03`, ou un `x` sans `y` sur la courbe, est refusé ici.
     */
    fun decompresser(octets: ByteArray): PublicKey? {
        if (octets.size != Messages.CLE_OCTETS || (octets[0] != 0x02.toByte() && octets[0] != 0x03.toByte())) return null
        val x = BigInteger(1, octets.copyOfRange(1, 33))
        if (x >= P) return null
        // y² = x³ − 3x + b, et p ≡ 3 (mod 4) : la racine est a^((p+1)/4).
        val y2 = x.modPow(BigInteger.valueOf(3), P).add(A.multiply(x)).add(B).mod(P)
        var y = y2.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        if (y.modPow(BigInteger.TWO, P) != y2) return null
        if (y.testBit(0) != (octets[0] == 0x03.toByte())) y = P.subtract(y)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), PARAMETRES))
    }

    /** `SEQUENCE { INTEGER r, INTEGER s }` → `r ‖ s`, chacun sur trente-deux octets. */
    fun deplierDER(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "pas une signature DER" }
        var i = 2
        fun entier(): ByteArray {
            require(der[i] == 0x02.toByte()) { "INTEGER attendu" }
            val longueur = der[i + 1].toInt() and 0xFF
            val valeur = der.copyOfRange(i + 2, i + 2 + longueur)
            i += 2 + longueur
            // Un zéro de tête n'est qu'un signe ; une valeur qui n'y tient pas n'est pas sur P-256.
            val sansSigne = valeur.dropWhile { it == 0.toByte() }.toByteArray()
            require(sansSigne.size <= 32) { "entier trop long" }
            return ByteArray(32 - sansSigne.size) + sansSigne
        }
        val r = entier()
        val s = entier()
        return r + s
    }

    /** `r ‖ s` → DER, pour donner à `java.security.Signature` ce qu'il attend. */
    fun plierDER(rs: ByteArray): ByteArray {
        require(rs.size == Messages.SIGNATURE_OCTETS)
        fun entier(v: ByteArray): ByteArray {
            val sans = v.dropWhile { it == 0.toByte() }.toByteArray().let { if (it.isEmpty()) byteArrayOf(0) else it }
            val corps = if (sans[0] < 0) byteArrayOf(0) + sans else sans
            return byteArrayOf(0x02, corps.size.toByte()) + corps
        }
        val corps = entier(rs.copyOfRange(0, 32)) + entier(rs.copyOfRange(32, 64))
        return byteArrayOf(0x30, corps.size.toByte()) + corps
    }

    /** Cette signature `r ‖ s` vérifie-t-elle sous cette clé compressée, pour ce message ? */
    fun verifie(cle: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != Messages.SIGNATURE_OCTETS) return false
        val publique = decompresser(cle) ?: return false
        return runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publique)
                update(message)
                verify(plierDER(signature))
            }
        }.getOrDefault(false)
    }
}
