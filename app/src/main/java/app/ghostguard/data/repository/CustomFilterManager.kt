package app.ghostguard.data.repository

import android.content.Context
import app.ghostguard.data.dao.FilterListDao
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.remote.api.CustomFilterApi
import app.ghostguard.data.remote.api.CustomFilterException
import app.ghostguard.utils.ZipUtils
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class CustomFilterManager(
    private val context: Context,
    private val client: HttpClient,
    private val filterListDao: FilterListDao,
    private val customFilterApi: CustomFilterApi,
) {
    companion object {
        private const val CUSTOM_FILTERS_DIR = "custom_filters"
        private const val REMOTE_FILTERS_DIR = "remote_filters"
    }

    private val localCompiler = LocalFilterCompiler(context, client, filterListDao)

    suspend fun addCustomFilter(
        url: String,
        displayName: String? = null,
    ): Result<FilterList> =
        withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()

            try {
                val existing = filterListDao.getByOriginalUrl(trimmedUrl)
                if (existing != null) {
                    return@withContext Result.failure(
                        CustomFilterException("Filter already exists: ${existing.name}"),
                    )
                }

                Timber.d("Building custom filter for URL: $trimmedUrl")
                val buildResponse = customFilterApi.buildFilter(trimmedUrl)
                Timber.d("Build success: downloadUrl=${buildResponse.downloadUrl}, rules=${buildResponse.ruleCount}")

                val sanitizedName = sanitizeName(trimmedUrl)
                val extractDir = File(context.filesDir, "$CUSTOM_FILTERS_DIR/$sanitizedName")
                if (extractDir.exists()) {
                    extractDir.deleteRecursively()
                }

                Timber.d("Downloading ZIP to: ${extractDir.absolutePath}")
                val extractedFiles =
                    ZipUtils.downloadAndExtractZip(
                        client = client,
                        downloadUrl = buildResponse.downloadUrl,
                        destDir = extractDir,
                    )
                Timber.d("Extracted ${extractedFiles.size} files")

                val infoJson = extractedFiles.find { it.name == "info.json" }
                val filterInfo =
                    if (infoJson != null && infoJson.exists()) {
                        parseInfoJson(infoJson.readText())
                    } else {
                        FilterInfo(
                            name = deriveFilterName(trimmedUrl),
                            url = trimmedUrl,
                            ruleCount = buildResponse.ruleCount,
                            updatedAt = System.currentTimeMillis().toString(),
                        )
                    }

                val finalName = displayName?.takeIf { it.isNotBlank() } ?: filterInfo.name

                val filterEntity =
                    FilterList(
                        name = finalName,
                        url = trimmedUrl,
                        description = "Custom filter: $finalName",
                        isEnabled = true,
                        isBuiltIn = false,
                        category = FilterList.CATEGORY_AD,
                        ruleCount = filterInfo.ruleCount,
                        domainCount = filterInfo.ruleCount,
                        bloomUrl = "",
                        trieUrl = "",
                        cssUrl = "",
                        originalUrl = trimmedUrl,
                        lastUpdated = System.currentTimeMillis(),
                    )

                val insertedId = filterListDao.insert(filterEntity)
                Timber.d("Inserted custom filter with id=$insertedId")

                val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR).apply { mkdirs() }

                val trieFile = extractedFiles.find { it.extension == "trie" }
                val bloomFile = extractedFiles.find { it.extension == "bloom" }
                val cssFile = extractedFiles.find { it.extension == "css" }

                trieFile?.let {
                    val dest = File(remoteFilterDir, "$insertedId.trie")
                    it.copyTo(dest, overwrite = true)
                    Timber.d("Copied trie → ${dest.absolutePath}")
                }
                bloomFile?.let {
                    val dest = File(remoteFilterDir, "$insertedId.bloom")
                    it.copyTo(dest, overwrite = true)
                    Timber.d("Copied bloom → ${dest.absolutePath}")
                }
                cssFile?.let {
                    val dest = File(remoteFilterDir, "$insertedId.css")
                    it.copyTo(dest, overwrite = true)
                    Timber.d("Copied css → ${dest.absolutePath}")
                }

                val updatedEntity =
                    filterEntity.copy(
                        id = insertedId,
                        bloomUrl = if (bloomFile != null) "local://$insertedId.bloom" else "",
                        trieUrl = if (trieFile != null) "local://$insertedId.trie" else "",
                        cssUrl = if (cssFile != null) "local://$insertedId.css" else "",
                    )
                filterListDao.update(updatedEntity)

                extractDir.deleteRecursively()
                Timber.d("Custom filter added successfully: ${filterInfo.name} (id=$insertedId)")

                Result.success(updatedEntity)
            } catch (e: CustomFilterException) {
                Timber.e(e, "Custom filter API error, trying local compile")
                localCompiler.addLocally(trimmedUrl, displayName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add custom filter via API, trying local compile")
                localCompiler.addLocally(trimmedUrl, displayName)
            }
        }

    suspend fun deleteCustomFilter(filter: FilterList) =
        withContext(Dispatchers.IO) {
            try {
                val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR)
                File(remoteFilterDir, "${filter.id}.trie").delete()
                File(remoteFilterDir, "${filter.id}.bloom").delete()
                File(remoteFilterDir, "${filter.id}.css").delete()

                filterListDao.delete(filter)
                Timber.d("Deleted custom filter: ${filter.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete custom filter: ${filter.name}")
            }
        }

    suspend fun editCustomFilter(
        filter: FilterList,
        newName: String,
        newUrl: String,
    ): Result<FilterList> =
        withContext(Dispatchers.IO) {
            try {
                val trimmedUrl = newUrl.trim()
                val trimmedName = newName.trim()

                if (trimmedUrl == filter.originalUrl || trimmedUrl == filter.url) {
                    if (trimmedName != filter.name) {
                        val updated = filter.copy(name = trimmedName, lastUpdated = System.currentTimeMillis())
                        filterListDao.update(updated)
                        return@withContext Result.success(updated)
                    }
                    return@withContext Result.success(filter)
                }

                val existingByUrl = filterListDao.getByUrl(trimmedUrl)
                val existingByOriginalUrl = filterListDao.getByOriginalUrl(trimmedUrl)

                if ((existingByUrl != null && existingByUrl.id != filter.id) ||
                    (existingByOriginalUrl != null && existingByOriginalUrl.id != filter.id)
                ) {
                    return@withContext Result.failure(
                        CustomFilterException("Filter already exists"),
                    )
                }

                val buildResponse = customFilterApi.buildFilter(trimmedUrl)

                val sanitizedName = sanitizeName(trimmedUrl)
                val extractDir = File(context.filesDir, "$CUSTOM_FILTERS_DIR/$sanitizedName")
                if (extractDir.exists()) extractDir.deleteRecursively()

                val extractedFiles =
                    ZipUtils.downloadAndExtractZip(
                        client = client,
                        downloadUrl = buildResponse.downloadUrl,
                        destDir = extractDir,
                    )

                val infoJson = extractedFiles.find { it.name == "info.json" }
                val filterInfo =
                    if (infoJson != null && infoJson.exists()) {
                        parseInfoJson(infoJson.readText())
                    } else {
                        null
                    }

                val finalName = if (trimmedName.isNotEmpty()) trimmedName else filterInfo?.name ?: deriveFilterName(trimmedUrl)
                val ruleCount = filterInfo?.ruleCount ?: buildResponse.ruleCount

                val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR).apply { mkdirs() }
                val trieFile = extractedFiles.find { it.extension == "trie" }
                val bloomFile = extractedFiles.find { it.extension == "bloom" }
                val cssFile = extractedFiles.find { it.extension == "css" }

                trieFile?.copyTo(File(remoteFilterDir, "${filter.id}.trie"), overwrite = true)
                bloomFile?.copyTo(File(remoteFilterDir, "${filter.id}.bloom"), overwrite = true)
                cssFile?.copyTo(File(remoteFilterDir, "${filter.id}.css"), overwrite = true)

                val updated =
                    filter.copy(
                        name = finalName,
                        url = trimmedUrl,
                        originalUrl = trimmedUrl,
                        ruleCount = ruleCount,
                        domainCount = ruleCount,
                        bloomUrl = if (bloomFile != null) "local://${filter.id}.bloom" else filter.bloomUrl,
                        trieUrl = if (trieFile != null) "local://${filter.id}.trie" else filter.trieUrl,
                        cssUrl = if (cssFile != null) "local://${filter.id}.css" else filter.cssUrl,
                        lastUpdated = System.currentTimeMillis(),
                    )
                filterListDao.update(updated)

                extractDir.deleteRecursively()

                Timber.d("Edited custom filter: $finalName, newUrl=$trimmedUrl")
                Result.success(updated)
            } catch (e: CustomFilterException) {
                Timber.e(e, "Custom filter API error on edit")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Failed to edit custom filter")
                Result.failure(CustomFilterException("Failed to edit filter: ${e.message}", e))
            }
        }

    suspend fun updateCustomFilter(filter: FilterList): Result<FilterList> =
        withContext(Dispatchers.IO) {
            val isLocallyBuilt =
                filter.trieUrl.startsWith("local://") &&
                    filter.bloomUrl.startsWith("local://")

            if (isLocallyBuilt) {
                return@withContext localCompiler.updateLocally(filter)
            }

            try {
                val url = filter.originalUrl.ifEmpty { filter.url }

                val buildResponse = customFilterApi.buildFilter(url)

                val sanitizedName = sanitizeName(url)
                val extractDir = File(context.filesDir, "$CUSTOM_FILTERS_DIR/$sanitizedName")
                if (extractDir.exists()) extractDir.deleteRecursively()

                val extractedFiles =
                    ZipUtils.downloadAndExtractZip(
                        client = client,
                        downloadUrl = buildResponse.downloadUrl,
                        destDir = extractDir,
                    )

                val infoJson = extractedFiles.find { it.name == "info.json" }
                val ruleCount =
                    if (infoJson != null && infoJson.exists()) {
                        parseInfoJson(infoJson.readText()).ruleCount
                    } else {
                        buildResponse.ruleCount
                    }

                val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR)
                extractedFiles.find { it.extension == "trie" }?.copyTo(
                    File(remoteFilterDir, "${filter.id}.trie"),
                    overwrite = true,
                )
                extractedFiles.find { it.extension == "bloom" }?.copyTo(
                    File(remoteFilterDir, "${filter.id}.bloom"),
                    overwrite = true,
                )
                extractedFiles.find { it.extension == "css" }?.copyTo(
                    File(remoteFilterDir, "${filter.id}.css"),
                    overwrite = true,
                )

                val updated =
                    filter.copy(
                        ruleCount = ruleCount,
                        domainCount = ruleCount,
                        lastUpdated = System.currentTimeMillis(),
                    )
                filterListDao.update(updated)

                extractDir.deleteRecursively()

                Timber.d("Updated custom filter: ${filter.name}, rules=$ruleCount")
                Result.success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update custom filter: ${filter.name}")
                Result.failure(CustomFilterException("Update failed: ${e.message}", e))
            }
        }

    suspend fun recompileLocally(filter: FilterList): Result<FilterList> = localCompiler.recompileLocally(filter)

    suspend fun addCustomFilterLocally(
        url: String,
        displayName: String?,
    ): Result<FilterList> = localCompiler.addLocally(url, displayName)

    suspend fun enqueueLocalCompile(
        url: String,
        name: String,
    ) = localCompiler.enqueueLocalCompile(url, name)

    fun enqueueRecompileLocally(filter: FilterList) = localCompiler.enqueueRecompileLocally(filter)
}
