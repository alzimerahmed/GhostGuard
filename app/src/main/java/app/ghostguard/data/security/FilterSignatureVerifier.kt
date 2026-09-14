package app.ghostguard.data.security

import timber.log.Timber
import java.io.File
import java.io.InputStream
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
    private val acceptedPublicKeyHex: Set<String> = SigningKeys.ACCEPTED_FILTER_SIGNING_PUBLIC_KEYS_HEX,
    @Deprecated("Use acceptedPublicKeyHex; kept for single-call convenience.")
    private val publicKeyHex: String = SigningKeys.FILTER_SIGNING_PUBLIC_KEY_HEX,
) {
    sealed class Result {
        data object Valid : Result()

        data class Invalid(
            val reason: String,
        ) : Result()
    }

    private val acceptedPublicKeys: List<ByteArray> =
        (acceptedPublicKeyHex + publicKeyHex).distinct().map { hexToBytes(it) }

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
        return if (acceptedPublicKeys.any { Ed25519.verify(it, signedPayload, signature) }) {
            Result.Valid
        } else {
            Result.Invalid("Ed25519 signature verification failed")
        }
    }

    /**
     * Verifies [file] against [signatureFileText], streaming the file through
     * SHA-256 so large filter artifacts (.bloom/.trie, tens of MB) are not held
     * in RAM. Ed25519 only signs the small `GG-SIG1\n<digest>` payload.
     */
    fun verifyFile(
        file: File,
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
        val actualDigest =
            try {
                file.inputStream().use { sha256HexStream(it) }
            } catch (e: Exception) {
                return Result.Invalid("Could not read artifact for hashing: ${e.message}")
            }
        if (actualDigest != expectedDigest) {
            Timber.w("SHA-256 mismatch: expected=%s actual=%s", expectedDigest, actualDigest)
            return Result.Invalid("SHA-256 digest mismatch (content corrupted or substituted)")
        }
        val signedPayload = "$FORMAT_ID\n$expectedDigest".toByteArray(Charsets.US_ASCII)
        return if (acceptedPublicKeys.any { Ed25519.verify(it, signedPayload, signature) }) {
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
        return verifyFile(file, sigText)
    }

    companion object {
        const val FORMAT_ID = "GG-SIG1"

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Streams [input] through SHA-256 without loading the whole file into RAM. */
        fun sha256HexStream(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string has odd length: ${hex.length}" }
            return ByteArray(hex.length / 2) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
        }
    }
}
