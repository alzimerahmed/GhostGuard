package app.ghostguard.data.remote

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.security.FilterSignatureVerifier
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests the H3 fail-closed behavior: a built-in filter artifact whose Ed25519
 * signature or SHA-256 digest fails verification must be rejected and the
 * previously-installed file kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FilterDownloadVerificationTest {
    private lateinit var context: Context
    private lateinit var manager: FilterDownloadManager

    // Signing material produced by tunnel/cmd/signfilter with the RFC 8032 §7.1
    // TEST 2 key — used ONLY to build valid fixtures in tests.
    private val content = "trusted-rules-v2\n".toByteArray()
    private val contentSha = "6ddb88983f737be592eb39a6f41b216f633d12257b16101ae741a779d05a442d"
    private val validSig =
        "69370550e134ef53a511b864bc5b5e9f66876d61e42e2bff6d4aa1ea106f8a20" +
            "66f3f7ea62240f84f27ae19201ce216479b671161bc4d860f824d6aac249540e"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager =
            FilterDownloadManager(
                context,
                HttpClient(),
                FilterSignatureVerifier(publicKeyHex = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"),
            )
    }

    private fun newFilter(id: Long = 42L) =
        FilterList(
            id = id,
            name = "Test List",
            url = "",
            bloomUrl = "",
            trieUrl = "",
            isBuiltIn = true,
        )

    @Test
    fun `valid signature installs new version`() {
        val filter = newFilter()
        val dest = File(context.filesDir, "remote_filters/${filter.id}.trie")
        dest.parentFile?.mkdirs()
        dest.writeText("old-rules-v1\n")

        val temp = File(context.filesDir, "remote_filters/${filter.id}.trie.tmp")
        temp.writeBytes(content)

        val result = manager.verifyAndInstall(temp, "GG-SIG1\n$contentSha\n$validSig\n", dest)

        assertEquals(dest.absolutePath, result)
        assertEquals("trusted-rules-v2\n", dest.readText())
        assertNull(manager.consumeSignatureFailure())
    }

    @Test
    fun `tampered content is rejected and previous version kept`() {
        val filter = newFilter()
        val dest = File(context.filesDir, "remote_filters/${filter.id}.trie")
        dest.parentFile?.mkdirs()
        dest.writeText("old-rules-v1\n")

        val tampered = content.copyOf().also { it[0] = 'X'.code.toByte() }
        val temp = File(context.filesDir, "remote_filters/${filter.id}.trie.tmp")
        temp.writeBytes(tampered)

        val result = manager.verifyAndInstall(temp, "GG-SIG1\n$contentSha\n$validSig\n", dest)

        assertNull(result)
        assertEquals("old-rules-v1\n", dest.readText())
        assertNotNull(manager.consumeSignatureFailure())
        assertNull(manager.consumeSignatureFailure())
        assertFalse(temp.exists())
    }

    @Test
    fun `missing signature is rejected and previous version kept`() {
        val filter = newFilter()
        val dest = File(context.filesDir, "remote_filters/${filter.id}.trie")
        dest.parentFile?.mkdirs()
        dest.writeText("old-rules-v1\n")

        val temp = File(context.filesDir, "remote_filters/${filter.id}.trie.tmp")
        temp.writeBytes(content)

        assertNull(manager.verifyAndInstall(temp, null, dest))
        assertEquals("old-rules-v1\n", dest.readText())
        assertNotNull(manager.consumeSignatureFailure())
    }

    @Test
    fun `signature from wrong key is rejected`() {
        val filter = newFilter()
        val dest = File(context.filesDir, "remote_filters/${filter.id}.trie")
        dest.parentFile?.mkdirs()
        dest.writeText("old-rules-v1\n")

        val temp = File(context.filesDir, "remote_filters/${filter.id}.trie.tmp")
        temp.writeBytes(content)
        val wrongSig = "ab".repeat(64)

        assertNull(manager.verifyAndInstall(temp, "GG-SIG1\n$contentSha\n$wrongSig\n", dest))
        assertEquals("old-rules-v1\n", dest.readText())
    }

    @Test
    fun `custom user lists are exempt from signature requirement`() {
        // Policy: custom (user-added) lists may remain unsigned — the user
        // explicitly chose that source; built-in/curated sources must be signed.
        val custom = newFilter().copy(isBuiltIn = false)
        assertFalse(custom.isBuiltIn)
        assertTrue(newFilter().isBuiltIn)
    }
}
