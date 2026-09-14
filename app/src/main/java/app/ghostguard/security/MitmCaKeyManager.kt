package app.ghostguard.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Protects the MITM Root CA private key at rest (security audit C1).
 *
 * The Go tunnel core needs the raw ECDSA key in memory to sign leaf
 * certificates, so the key cannot live inside Android Keystore.
 * Instead, a Keystore-held AES-256-GCM KEK (StrongBox-backed when
 * available) wraps the CA key; only the wrapped blob is persisted to
 * filesDir/ca.key.enc. The plaintext key exists solely in memory while
 * the tunnel runs and is never written to disk — fresh CAs are
 * generated in memory by the tunnel core ([tunnel.Engine.generateMitmCAInMemory])
 * and wrapped here before first use.
 *
 * Legacy migration: installs upgraded from the plaintext ca.key path
 * are migrated transparently — the key is read by Go once, wrapped
 * here, and the plaintext file is deleted. The installed CA
 * certificate stays valid, so no user action is required.
 *
 * Residual exposure: the key crosses the Go↔Kotlin boundary as an
 * immutable String and cannot be zeroed in either runtime. It exists
 * in memory only while the tunnel runs; getMitmCAKey() is called
 * exactly once per key lifetime.
 */
object MitmCaKeyManager {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEK_ALIAS = "ghostguard_mitm_ca_kek"
    private const val WRAPPED_FILE = "ca.key.enc"
    private const val LEGACY_KEY_FILE = "ca.key"
    private const val KEY_PEM_HEADER = "-----BEGIN EC PRIVATE KEY-----"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val initLock = Any()
    private val keystoreLock = Any()

    /**
     * Starts stack MITM using the Keystore-protected key path.
     * Returns the CA certificate PEM (empty on failure).
     * Must be called off the main thread.
     */
    suspend fun startStackMitmSecure(
        context: Context,
        engine: tunnel.Engine,
        certDir: String,
    ): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            startStackMitmSecureBlocking(context, engine, certDir)
        }

    /**
     * Blocking variant for non-coroutine callers (VPN service boot,
     * which already runs on a background thread).
     */
    fun startStackMitmSecureBlocking(
        context: Context,
        engine: tunnel.Engine,
        certDir: String,
    ): String =
        synchronized(initLock) {
            val wrappedFile = File(context.filesDir, WRAPPED_FILE)
            val legacyFile = File(context.filesDir, LEGACY_KEY_FILE)

            when {
                wrappedFile.exists() -> {
                    // Steady state: unwrap and start. A corrupt blob or a
                    // broken Keystore is an error — regenerating would
                    // silently invalidate the user's installed CA cert.
                    val keyPem = unwrapKey(wrappedFile)
                    require(keyPem.startsWith(KEY_PEM_HEADER)) { "Unwrapped CA key has unexpected format" }
                    engine.startStackMitmWithKey(certDir, keyPem)
                }

                legacyFile.exists() -> {
                    // Legacy migration: Go loads the plaintext key (the file
                    // already exists), then we wrap it and delete plaintext.
                    val caPem = engine.startStackMitm(certDir)
                    val keyPem = engine.getMitmCAKey()
                    check(!keyPem.isNullOrEmpty()) { "Go engine did not expose generated CA key" }
                    wrapAndStore(context, keyPem)
                    caPem
                }

                else -> {
                    // Fresh install: generate the CA in memory (Go persists
                    // only the certificate), wrap the key, then start.
                    val pair = engine.generateMitmCAInMemory(certDir)
                    check(pair.isNotEmpty()) { "In-memory CA generation failed" }
                    val idx = pair.indexOf(KEY_PEM_HEADER)
                    check(idx > 0) { "Unexpected CA pair format" }
                    val certPem = pair.substring(0, idx)
                    val keyPem = pair.substring(idx)
                    wrapAndStore(context, keyPem)
                    engine.startStackMitmWithKey(certDir, keyPem).ifEmpty { certPem }
                }
            }
        }

    private fun unwrapKey(wrappedFile: File): String {
        val blob = wrappedFile.readBytes()
        require(blob.size > GCM_IV_BYTES) { "Wrapped CA key file too short" }
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKek(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Encrypts [keyPem] with the Keystore KEK and persists it. Throws on
     * failure — callers must not continue with an unprotected key.
     */
    private fun wrapAndStore(
        context: Context,
        keyPem: String,
    ) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKek())
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(keyPem.toByteArray(Charsets.UTF_8))
        val target = File(context.filesDir, WRAPPED_FILE)
        target.writeBytes(iv + ciphertext)
        require(target.length() > 0L) { "Wrapped CA key file is empty after write" }

        // Migration cleanup: remove the plaintext key only after the
        // wrapped blob is durably on disk.
        val legacy = File(context.filesDir, LEGACY_KEY_FILE)
        if (legacy.exists() && !legacy.delete()) {
            Timber.w("Failed to delete legacy plaintext CA key at %s", legacy.absolutePath)
        }
        Timber.i("MITM CA key wrapped into Keystore-encrypted storage")
    }

    private fun getOrCreateKek(): SecretKey =
        synchronized(keystoreLock) {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            (keyStore.getEntry(KEK_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec =
                KeyGenParameterSpec
                    .Builder(KEK_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    generator.init(spec.setIsStrongBoxBacked(true).build())
                    return generator.generateKey()
                }
            } catch (e: Exception) {
                Timber.d(e, "StrongBox key generation failed — falling back to TEE-backed Keystore key")
            }
            generator.init(spec.build())
            generator.generateKey()
        }
}
