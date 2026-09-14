package app.ghostguard.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ed25519 verification tests against RFC 8032 test vectors plus a
 * Go-generated signature over a non-empty message (same RFC test key).
 */
class Ed25519Test {
    companion object {
        // RFC 8032 §7.1 TEST 1 (empty message)
        private const val PUB_1 = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        private const val SIG_1 =
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

        // RFC 8032 §7.1 TEST 2 key (seed-derived), used with a non-empty message
        private const val PUB_2 = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
        private val MESSAGE =
            "||ads.example.com^\n||tracker.example.net^\n".toByteArray(Charsets.UTF_8)
        private const val SIG_MSG =
            "9cb2b7d69712ead4e8d9ca965640806cdd5e45901063d51b7563959c1ff1e6fc" +
                "502cd98357d6dc2d252ef546236c444c1c5db4f69de5eba7d9ba4369408d600f"
    }

    @Test
    fun `valid signature over empty message verifies`() {
        val pub = FilterSignatureVerifier.hexToBytes(PUB_1)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_1)
        assertTrue(Ed25519.verify(pub, ByteArray(0), sig))
    }

    @Test
    fun `valid signature over non-empty message verifies`() {
        val pub = FilterSignatureVerifier.hexToBytes(PUB_2)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_MSG)
        assertTrue(Ed25519.verify(pub, MESSAGE, sig))
    }

    @Test
    fun `tampered message fails verification`() {
        val pub = FilterSignatureVerifier.hexToBytes(PUB_2)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_MSG)
        val tampered = MESSAGE.copyOf().also { it[0] = 'X'.code.toByte() }
        assertFalse(Ed25519.verify(pub, tampered, sig))
    }

    @Test
    fun `wrong key fails verification`() {
        val wrongPub = FilterSignatureVerifier.hexToBytes(PUB_1)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_MSG)
        assertFalse(Ed25519.verify(wrongPub, MESSAGE, sig))
    }

    @Test
    fun `tampered signature fails verification`() {
        val pub = FilterSignatureVerifier.hexToBytes(PUB_2)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_MSG).copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertFalse(Ed25519.verify(pub, MESSAGE, sig))
    }

    @Test
    fun `malformed inputs are rejected`() {
        val pub = FilterSignatureVerifier.hexToBytes(PUB_2)
        val sig = FilterSignatureVerifier.hexToBytes(SIG_MSG)
        assertFalse(Ed25519.verify(pub.copyOfRange(0, 31), MESSAGE, sig))
        assertFalse(Ed25519.verify(pub, MESSAGE, sig.copyOfRange(0, 63)))
        assertFalse(Ed25519.verify(pub, MESSAGE, ByteArray(64)))
    }

    @Test
    fun `hex round trip`() {
        val hex = "00ff10af"
        assertTrue(FilterSignatureVerifier.hexToBytes(hex).contentEquals(byteArrayOf(0, -1, 0x10, -0x51)))
        assertEquals(
            hex,
            FilterSignatureVerifier.hexToBytes(hex).joinToString("") { "%02x".format(it) },
        )
    }
}
