package app.ghostguard.data.security

import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Verifies downloaded filter/scriptlet artifacts before they are persisted or
 * handed to the Go engine (`setTries`) or injected as JS in the WebView.
 *
 * Signature file format (detached `<name>.sig`, plain text):
 * ```
 * GG-SIG1
 * <sha256-hex of the artifact bytes>
 * <ed25519-hex signature over the ASCII line "GG-SIG1\n<sha256-hex>">
 * ```
 *
 * SHA-256 gives integrity (detect truncated/corrupted downloads); Ed25519 gives
 * authenticity (only the GhostGuard release key can produce a valid digest).
 * Both must pass — failure is fail-closed.
 *
 * Policy: built-in/curated sources MUST be signed. User-added custom lists may
 * remain unsigned (the user's own choice of source); see docs/research.md.
 */
class FilterSignatureVerifier(
    private val publicKeyHex: String = SigningKeys.FILTER_SIGNING_PUBLIC_KEY_HEX,
) {
    sealed class Result {
        data object Valid : Result()

        data class Invalid(
            val reason: String,
        ) : Result()
    }

    private val publicKey: ByteArray = hexToBytes(publicKeyHex)

    /** Verifies [content] against the given signature file text. */
    fun verify(
        content: ByteArray,
        signatureFileText: String,
    ): Result {
        val lines = signatureFileText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size != 3 || lines[0] != FORMAT_ID) {
            return Result.Invalid("Malformed signature file (expected $FORMAT_ID header + digest + signature)")
        }
        val expectedDigest = lines[1].lowercase()
        val signature =
            runCatching { hexToBytes(lines[2]) }.getOrNull()
                ?: return Result.Invalid("Signature is not valid hex")

        if (signature.size != 64) {
            return Result.Invalid("Signature has wrong length (${signature.size} bytes, expected 64)")
        }

        val actualDigest = sha256Hex(content)
        if (actualDigest != expectedDigest) {
            Timber.w("SHA-256 mismatch: expected=%s actual=%s", expectedDigest, actualDigest)
            return Result.Invalid("SHA-256 digest mismatch (content corrupted or substituted)")
        }

        val signedPayload = "$FORMAT_ID\n$expectedDigest".toByteArray(Charsets.US_ASCII)
        return if (Ed25519.verify(publicKey, signedPayload, signature)) {
            Result.Valid
        } else {
            Result.Invalid("Ed25519 signature verification failed")
        }
    }

    /** Verifies [file] using the detached signature at [file].sig. */
    fun verifyFile(file: File): Result {
        val sigFile = File(file.parentFile, "${file.name}.sig")
        if (!sigFile.exists()) {
            return Result.Invalid("Missing signature file: ${sigFile.name}")
        }
        val sigText =
            runCatching { sigFile.readText() }.getOrElse {
                return Result.Invalid("Could not read signature file: ${it.message}")
            }
        return verify(file.readBytes(), sigText)
    }

    companion object {
        const val FORMAT_ID = "GG-SIG1"

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
    }
}
