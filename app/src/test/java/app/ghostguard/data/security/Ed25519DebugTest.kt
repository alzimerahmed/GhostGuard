package app.ghostguard.data.security

import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest

class Ed25519DebugTest {
    @Test
    fun debug() {
        val pub = FilterSignatureVerifier.hexToBytes("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val msg = "||ads.example.com^\n||tracker.example.net^\n".toByteArray()
        val sig =
            FilterSignatureVerifier.hexToBytes(
                "9cb2b7d69712ead4e8d9ca965640806cdd5e45901063d51b7563959c1ff1e6fc" +
                    "502cd98357d6dc2d252ef546236c444c1c5db4f69de5eba7d9ba4369408d600f",
            )
        val r = Ed25519.decodePoint(sig.copyOfRange(0, 32))!!
        val a = Ed25519.decodePoint(pub)!!
        val digest =
            MessageDigest
                .getInstance("SHA-512")
                .digest(Ed25519.encodePoint(r) + Ed25519.encodePoint(a) + msg)
        println("K16 ${digest.take(16).joinToString("") { "%02x".format(it) }}")
        val kA = Ed25519.scalarMultiply(BigInteger(1, digest), Ed25519.BASE)
        println("KA  ${Ed25519.encodePoint(kA).joinToString("") { "%02x".format(it) }}")
        val twoA = Ed25519.scalarMultiply(BigInteger.valueOf(2), a)
        println("2A  ${Ed25519.encodePoint(twoA).joinToString("") { "%02x".format(it) }}")
        val sum = Ed25519.add(a, a)
        println("A+A ${Ed25519.encodePoint(sum).joinToString("") { "%02x".format(it) }}")
        println("NEUTRALADD ${Ed25519.encodePoint(Ed25519.add(a, Ed25519.NEUTRAL)).joinToString("") { "%02x".format(it) }}")
        println("PUB  ${pub.joinToString("") { "%02x".format(it) }}")
    }
}
