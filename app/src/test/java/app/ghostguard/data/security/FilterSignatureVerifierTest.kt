package app.ghostguard.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FilterSignatureVerifierTest {
    private val testPubHex = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"

    // Values produced by tunnel/cmd/signfilter using the RFC 8032 §7.1 TEST 2 key.
    private val content = "||ads.example.com^\n||tracker.example.net^\n".toByteArray()
    private val contentSha = "30966f75b42422367675fbf20324a8ee0b41dbc16205bda936265c0647a4721f"
    private val contentSig =
        "65704aa393d099c51b8bd209a5bcb4b3638bce51da8a6b47d6ca9685e79a92f9" +
            "34d30636508b668dca5dc7b829da9a869fcc4a4fce86d39810aa2b423e058507"

    private fun sigText(
        digest: String = contentSha,
        sig: String = contentSig,
    ): String = "GG-SIG1\n$digest\n$sig\n"

    private fun verifier(pub: String = testPubHex) = FilterSignatureVerifier(publicKeyHex = pub)

    @Test
    fun `valid signature and digest verify`() {
        assertEquals(FilterSignatureVerifier.Result.Valid, verifier().verify(content, sigText()))
    }

    @Test
    fun `tampered content fails sha-256 check`() {
        val tampered = content.copyOf().also { it[3] = 'X'.code.toByte() }
        val result = verifier().verify(tampered, sigText())
        assertTrue(result is FilterSignatureVerifier.Result.Invalid)
        assertTrue((result as FilterSignatureVerifier.Result.Invalid).reason.contains("SHA-256"))
    }

    @Test
    fun `signature over different digest fails`() {
        // Valid Ed25519 signature, but over the wrong digest — must fail closed.
        val otherDigest = "f".repeat(64)
        val result = verifier().verify(content, sigText(digest = otherDigest))
        assertTrue(result is FilterSignatureVerifier.Result.Invalid)
    }

    @Test
    fun `wrong public key fails`() {
        val otherKey = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        val result = verifier(pub = otherKey).verify(content, sigText())
        assertTrue(result is FilterSignatureVerifier.Result.Invalid)
        assertTrue((result as FilterSignatureVerifier.Result.Invalid).reason.contains("Ed25519"))
    }

    @Test
    fun `missing sig file fails`() {
        val dir = createTempDirectory("gg-sig").toFile()
        val file = File(dir, "list.trie").apply { writeBytes(content) }
        val result = verifier().verifyFile(file)
        assertTrue(result is FilterSignatureVerifier.Result.Invalid)
        assertTrue((result as FilterSignatureVerifier.Result.Invalid).reason.contains("Missing signature"))
    }

    @Test
    fun `verifyFile accepts valid detached signature`() {
        val dir = createTempDirectory("gg-sig").toFile()
        val file = File(dir, "list.trie").apply { writeBytes(content) }
        File(dir, "list.trie.sig").writeText(sigText())
        assertEquals(FilterSignatureVerifier.Result.Valid, verifier().verifyFile(file))
    }

    @Test
    fun `malformed signature file fails`() {
        assertEquals(FilterSignatureVerifier.Result.Invalid::class, verifier().verify(content, "garbage")::class)
        assertEquals(
            FilterSignatureVerifier.Result.Invalid::class,
            verifier().verify(content, "WRONG-HDR\n$contentSha\n$contentSig")::class,
        )
        // Bad hex signature
        assertTrue(verifier().verify(content, sigText(sig = "zz".repeat(64))) is FilterSignatureVerifier.Result.Invalid)
        // Wrong-length signature
        assertTrue(verifier().verify(content, sigText(sig = "ab".repeat(32))) is FilterSignatureVerifier.Result.Invalid)
    }

    @Test
    fun `sha256Hex matches known digest`() {
        assertEquals(contentSha, FilterSignatureVerifier.sha256Hex(content))
    }
}
