package app.ghostguard.data.security

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Minimal, dependency-free Ed25519 signature verification (RFC 8032).
 *
 * Android's `java.security` only gained Ed25519 support on API 33+, and the app
 * supports minSdk 24, so verification is implemented here with BigInteger math.
 * This class intentionally supports verification ONLY — private keys never live
 * in the app.
 */
object Ed25519 {
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val L: BigInteger =
        BigInteger.valueOf(2).pow(252).add(
            BigInteger("27742317777372353535851937790883648493"),
        )
    private val D: BigInteger =
        BigInteger
            .valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P))
            .mod(P)
    private val SQRT_M1: BigInteger =
        BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P)

    private val BASE_X =
        BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202")
    private val BASE_Y =
        BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960")

    /** Extended homogeneous coordinates (X : Y : Z : T = X/Z, Y/Z, T = XY/Z). */
    internal data class Point(
        val x: BigInteger,
        val y: BigInteger,
        val z: BigInteger,
        val t: BigInteger,
    )

    internal val NEUTRAL = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    internal val BASE = Point(BASE_X, BASE_Y, BigInteger.ONE, BASE_X.multiply(BASE_Y).mod(P))

    /** Returns true if [signature] (64 bytes) is a valid Ed25519 signature of [message] by [publicKey] (32 bytes). */
    fun verify(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false

        val a = decodePoint(publicKey) ?: return false
        val r = decodePoint(signature.copyOfRange(0, 32)) ?: return false

        // s is encoded little-endian; reverse into big-endian for BigInteger.
        val sBytes = signature.copyOfRange(32, 64)
        sBytes.reverse()
        val s = BigInteger(1, sBytes)
        if (s >= L) return false

        val rBytes = encodePoint(r)
        val aBytes = encodePoint(a)
        val digest =
            MessageDigest
                .getInstance("SHA-512")
                .apply {
                    update(rBytes)
                    update(aBytes)
                    update(message)
                }.digest()

        // The challenge scalar k is the SHA-512 digest interpreted little-endian.
        val kBytes = digest.reversedArray()
        val k = BigInteger(1, kBytes).mod(L)
        // Check [S]B == R + [k]A.
        val sb = scalarMultiply(s, BASE)
        val rhs = add(r, scalarMultiply(k, a))
        return encodePoint(sb).contentEquals(encodePoint(rhs))
    }

    /** Unified addition (add-2008-hwcd-3, a = -1); safe for doubling. */
    internal fun add(
        p: Point,
        q: Point,
    ): Point {
        val a =
            p.y
                .subtract(p.x)
                .multiply(q.y.subtract(q.x))
                .mod(P)
        val b =
            p.y
                .add(p.x)
                .multiply(q.y.add(q.x))
                .mod(P)
        val c =
            p.t
                .multiply(q.t)
                .multiply(D)
                .multiply(BigInteger.valueOf(2))
                .mod(P)
        val d =
            p.z
                .multiply(q.z)
                .multiply(BigInteger.valueOf(2))
                .mod(P)
        val e = b.subtract(a).mod(P)
        val f = d.subtract(c).mod(P)
        val g = d.add(c).mod(P)
        val h = b.add(a).mod(P)
        return Point(
            x = e.multiply(f).mod(P),
            y = g.multiply(h).mod(P),
            z = f.multiply(g).mod(P),
            t = e.multiply(h).mod(P),
        )
    }

    internal fun scalarMultiply(
        scalar: BigInteger,
        point: Point,
    ): Point {
        var result = NEUTRAL
        var addend = point
        var k = scalar
        while (k.signum() > 0) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        return result
    }

    internal fun decodePoint(encoded: ByteArray): Point? {
        if (encoded.size != 32) return null
        val sign = (encoded[31].toInt() and 0x80) != 0
        val yBytes = encoded.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        // Ed25519 encodes little-endian; BigInteger(1, bytes) expects big-endian.
        yBytes.reverse()
        val y = BigInteger(1, yBytes)
        if (y >= P) return null

        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)

        // x = u * v^3 * (u * v^7)^((p-5)/8), with sqrt(-1) correction if needed.
        var x =
            u
                .multiply(v.modPow(BigInteger.valueOf(3), P))
                .multiply(u.multiply(v.modPow(BigInteger.valueOf(7), P)).modPow(P.subtract(BigInteger.valueOf(5)).divide(BigInteger.valueOf(8)), P))
                .mod(P)
        val vx2 = v.multiply(x.modPow(BigInteger.valueOf(2), P)).mod(P)
        x =
            if (vx2 == u) {
                x
            } else if (vx2 == P.subtract(u).mod(P)) {
                x.multiply(SQRT_M1).mod(P)
            } else {
                return null
            }
        if (x.signum() == 0 && sign) return null
        if (x.mod(BigInteger.valueOf(2)) != (if (sign) BigInteger.ONE else BigInteger.ZERO)) {
            x = P.subtract(x)
        }
        return Point(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    internal fun encodePoint(p: Point): ByteArray {
        val zInv = p.z.modInverse(P)
        val x = p.x.multiply(zInv).mod(P)
        val y = p.y.multiply(zInv).mod(P)
        val out = ByteArray(32)
        // toByteArray() is big-endian two's complement (possibly 33 bytes with leading zero);
        // copy the low 32 bytes then reverse into little-endian.
        val big = y.toByteArray()
        for (i in 0 until minOf(32, big.size)) {
            out[i] = big[big.size - 1 - i]
        }
        if (x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte()
        return out
    }
}
