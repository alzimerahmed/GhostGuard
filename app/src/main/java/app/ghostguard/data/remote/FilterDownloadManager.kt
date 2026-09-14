package app.ghostguard.data.remote

import android.content.Context
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.security.FilterSignatureVerifier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

data class DownloadedFilterPaths(
    val bloomPath: String?,
    val triePath: String?,
    val cssPath: String?,
    val scriptletPath: String?,
)

class FilterDownloadManager(
    private val context: Context,
    private val client: HttpClient,
    private val signatureVerifier: FilterSignatureVerifier = FilterSignatureVerifier(),
) {
    private val filterDir =
        File(context.filesDir, "remote_filters").apply {
            if (!exists()) mkdirs()
        }

    /** Reason of the most recent signature-verification failure, if any (H3). */
    @Volatile
    private var lastSignatureFailure: String? = null

    /** Returns and clears the most recent signature-verification failure reason. */
    fun consumeSignatureFailure(): String? {
        val reason = lastSignatureFailure
        lastSignatureFailure = null
        return reason
    }

    /**
     * Downloads the required filter files (.bloom, .trie, and optional .css / .scriptlets).
     * Automatically handles .zip archives if downloadUrl is provided.
     *
     * Built-in (curated) filters are REQUIRED to be Ed25519-signed + SHA-256 verified
     * (audit finding H3): any artifact failing verification is rejected and the
     * previously-cached files are kept (fail closed). Custom user-added lists are
     * exempt — the user explicitly chose that source.
     */
    suspend fun downloadFilterList(
        filter: FilterList,
        forceUpdate: Boolean = false,
    ): Result<DownloadedFilterPaths> =
        withContext(Dispatchers.IO) {
            try {
                // Security (H3): built-in sources must be signed; custom lists are the user's own choice.
                val requireSignature = filter.isBuiltIn
                lastSignatureFailure = null
                val bloomFile = File(filterDir, "${filter.id}.bloom")
                val trieFile = File(filterDir, "${filter.id}.trie")
                val cssFile = File(filterDir, "${filter.id}.css")
                val scriptletFile = File(filterDir, "${filter.id}.scriptlets")

                if (!forceUpdate && bloomFile.exists() && bloomFile.length() > 0 && trieFile.exists() && trieFile.length() > 0) {
                    Timber.d("Filter ${filter.id} already cached locally")
                    return@withContext Result.success(
                        DownloadedFilterPaths(
                            bloomPath = bloomFile.absolutePath,
                            triePath = trieFile.absolutePath,
                            cssPath = cssFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
                            scriptletPath = scriptletFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
                        ),
                    )
                }

                val zipUrl =
                    when {
                        filter.url.contains(".zip") -> filter.url
                        filter.bloomUrl.contains(".bloom") -> filter.bloomUrl.replace(".bloom", ".zip")
                        filter.trieUrl.contains(".trie") -> filter.trieUrl.replace(".trie", ".zip")
                        else -> ""
                    }

                if (zipUrl.isNotEmpty()) {
                    val zipSuccess = downloadAndExtractZip(zipUrl, bloomFile, trieFile, cssFile, scriptletFile, requireSignature)
                    if (zipSuccess && bloomFile.exists() && trieFile.exists()) {
                        return@withContext Result.success(
                            DownloadedFilterPaths(
                                bloomPath = bloomFile.absolutePath,
                                triePath = trieFile.absolutePath,
                                cssPath = cssFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
                                scriptletPath = scriptletFile.takeIf { it.exists() && it.length() > 0 }?.absolutePath,
                            ),
                        )
                    }
                }

                val bloomPath = if (filter.bloomUrl.isNotEmpty()) downloadFile(filter.bloomUrl, bloomFile, forceUpdate, requireSignature) else null
                val triePath = if (filter.trieUrl.isNotEmpty()) downloadFile(filter.trieUrl, trieFile, forceUpdate, requireSignature) else null

                var cssPath: String? = null
                if (filter.cssUrl.isNotEmpty()) {
                    cssPath = downloadFile(filter.cssUrl, cssFile, forceUpdate, requireSignature)
                }

                var scriptletPath: String? = null
                if (filter.scriptletsUrl.isNotEmpty()) {
                    scriptletPath = downloadFile(filter.scriptletsUrl, scriptletFile, forceUpdate, requireSignature)
                }

                if (bloomPath != null && triePath != null) {
                    Result.success(DownloadedFilterPaths(bloomPath, triePath, cssPath, scriptletPath))
                } else {
                    Result.failure(Exception("Failed to download core filter files (.bloom or .trie) for ${filter.id}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading filter list ${filter.id}")
                Result.failure(e)
            }
        }

    private suspend fun downloadAndExtractZip(
        url: String,
        bloomFile: File,
        trieFile: File,
        cssFile: File,
        scriptletFile: File,
        requireSignature: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val tempZip = File(filterDir, "temp_${System.currentTimeMillis()}.zip")
            try {
                Timber.d("Downloading filter zip from $url")
                val response = client.get(url)
                if (response.status.value !in 200..299) {
                    Timber.e("HTTP ${response.status.value} downloading zip $url")
                    return@withContext false
                }

                val channel = response.bodyAsChannel()
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (channel.readAvailable(buffer).also { bytesRead = it } >= 0) {
                        if (bytesRead > 0) output.write(buffer, 0, bytesRead)
                    }
                }

                // Security (H3): verify the archive signature BEFORE extracting anything.
                // The detached signature is fetched from `<zipUrl>.sig` (same scheme as the
                // per-file path) and verified with streamed SHA-256 so the archive is not
                // held in RAM. Fail closed — reject the update and keep the previous files.
                if (requireSignature) {
                    val sigResponse = runCatching { client.get("$url.sig") }.getOrNull()
                    val sigText =
                        if (sigResponse != null && sigResponse.status.value in 200..299) {
                            runCatching { sigResponse.bodyAsText() }.getOrNull()
                        } else {
                            null
                        }
                    val result =
                        if (sigText == null) {
                            FilterSignatureVerifier.Result.Invalid("Missing signature for filter archive")
                        } else {
                            signatureVerifier.verifyFile(tempZip, sigText)
                        }
                    if (result !is FilterSignatureVerifier.Result.Valid) {
                        val reason = (result as FilterSignatureVerifier.Result.Invalid).reason
                        Timber.e("Signature verification failed for filter zip $url — rejecting update: $reason")
                        lastSignatureFailure = "filter archive: $reason"
                        return@withContext false
                    }
                }

                // Atomic extraction: write every entry to a `.tmp` sibling first, then
                // rename all to their final destinations only after the whole archive
                // succeeded. A mid-extract failure leaves the previously-cached files
                // intact (same guarantee the per-file path provides).
                val staged = mutableMapOf<File, File>()
                try {
                    java.util.zip.ZipFile(tempZip).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val targetFile =
                                when {
                                    entry.name.endsWith(".bloom") -> bloomFile
                                    entry.name.endsWith(".trie") -> trieFile
                                    entry.name.endsWith(".css") -> cssFile
                                    entry.name.endsWith(".scriptlets") -> scriptletFile
                                    else -> null
                                }
                            targetFile?.let { out ->
                                val tmp = File(out.parent, "${out.name}.tmp")
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                                }
                                staged[out] = tmp
                            }
                        }
                    }
                    // All entries extracted cleanly — promote them atomically.
                    for ((dest, tmp) in staged) {
                        if (!tmp.renameTo(dest) && !(dest.delete() && tmp.renameTo(dest))) {
                            throw java.io.IOException("Failed to promote ${dest.name} after extraction")
                        }
                    }
                    Timber.d("Successfully extracted zip for filter")
                    true
                } catch (e: Exception) {
                    // Roll back any staged temps; leave the previous dest files untouched.
                    staged.values.forEach { runCatching { it.delete() } }
                    throw e
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download and extract filter zip: $url")
                false
            } finally {
                tempZip.delete()
            }
        }

    /**
     * Downloads a single file from the given URL and saves it to [destFile].
     * Uses a temporary file during download to prevent partial corruption.
     *
     * When [requireSignature] is true (built-in filters), a detached `<url>.sig`
     * is fetched and the downloaded bytes are verified (SHA-256 + Ed25519) BEFORE
     * the temp file replaces [destFile]. On any verification failure the temp file
     * is deleted and the previous [destFile] is left untouched (fail closed).
     */
    private suspend fun downloadFile(
        url: String,
        destFile: File,
        forceUpdate: Boolean,
        requireSignature: Boolean = false,
    ): String? {
        // Custom filters use "local://" sentinel URLs — files are already on disk
        if (url.startsWith("local://")) {
            return if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else null
        }

        if (!forceUpdate && destFile.exists() && destFile.length() > 0) {
            Timber.d("File already exists: ${destFile.name}")
            return destFile.absolutePath
        }

        return try {
            Timber.d("Downloading from $url to ${destFile.name}")
            val response = client.get(url)
            if (response.status.value !in 200..299) {
                Timber.e("HTTP ${response.status.value} downloading $url")
                return null
            }
            val channel = response.bodyAsChannel()

            val tempFile = File(destFile.parent, "${destFile.name}.tmp")
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (channel.readAvailable(buffer).also { bytesRead = it } >= 0) {
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }

            // Security (H3): verify signature before the artifact is persisted or
            // ever handed to the Go engine / WebView JS injection.
            if (requireSignature) {
                val sigResponse = runCatching { client.get("$url.sig") }.getOrNull()
                val sigText =
                    if (sigResponse != null && sigResponse.status.value in 200..299) {
                        runCatching { sigResponse.bodyAsText() }.getOrNull()
                    } else {
                        null
                    }
                // verifyAndInstall atomically replaces destFile only on success.
                return verifyAndInstall(tempFile, sigText, destFile)
            }

            if (tempFile.renameTo(destFile)) {
                Timber.d("Successfully downloaded to ${destFile.absolutePath}")
                destFile.absolutePath
            } else {
                Timber.e("Failed to rename temp file to ${destFile.name}")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download $url")
            null
        }
    }

    /**
     * Verifies [tempFile] against [sigText] and, only on success, atomically
     * replaces [destFile]. On any failure the previous [destFile] is kept and
     * the failure reason is recorded for [consumeSignatureFailure].
     * Returns [destFile]'s path on success, null on failure.
     */
    internal fun verifyAndInstall(
        tempFile: File,
        sigText: String?,
        destFile: File,
    ): String? {
        val result =
            if (sigText == null) {
                FilterSignatureVerifier.Result.Invalid("Missing signature for ${destFile.name}")
            } else {
                signatureVerifier.verifyFile(tempFile, sigText)
            }
        val failure = result as? FilterSignatureVerifier.Result.Invalid
        if (failure != null) {
            Timber.e("Signature verification failed for ${destFile.name}: ${failure.reason} — keeping previous version")
            lastSignatureFailure = "${destFile.name}: ${failure.reason}"
            tempFile.delete()
            return null
        }
        // Windows renameTo fails when the destination exists; fall back to delete+rename.
        if (tempFile.renameTo(destFile) || (destFile.delete() && tempFile.renameTo(destFile))) {
            return destFile.absolutePath
        }
        return null
    }

    /**
     * Reads a downloaded CSS file containing raw selectors, appends { display: none !important; }
     * and returns a single valid CSS string ready for injection.
     */
    fun getInjectableCss(file: File): String {
        if (!file.exists() || file.length() == 0L) {
            return ""
        }

        val cssBuilder = StringBuilder()
        try {
            file.forEachLine { line ->
                var selector = line.trim()
                if (selector.isEmpty() || selector.startsWith("!") || (selector.startsWith("#") && !selector.startsWith("##"))) {
                    return@forEachLine
                }
                if (selector.startsWith("##")) {
                    selector = selector.removePrefix("##").trim()
                }
                // Skip unhandled domain-specific rules (e.g. domain.com##...) or complex rules
                if (selector.isNotEmpty() && !selector.contains("##")) {
                    cssBuilder.append(selector).append(" { display: none !important; }\n")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading CSS file ${file.absolutePath}")
            return ""
        }
        return cssBuilder.toString()
    }
}
