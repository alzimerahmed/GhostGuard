package app.ghostguard.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.ghostguard.data.dao.FilterListDao
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.remote.api.CustomFilterException
import app.ghostguard.worker.FilterCompileWorker
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class LocalFilterCompiler(
    private val context: Context,
    private val client: HttpClient,
    private val filterListDao: FilterListDao,
) {
    companion object {
        private const val REMOTE_FILTERS_DIR = "remote_filters"
    }

    suspend fun addLocally(
        url: String,
        displayName: String?,
    ): Result<FilterList> =
        withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()
            val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR).apply { mkdirs() }
            val tempFile = File(context.cacheDir, "filter_download_${System.currentTimeMillis()}.txt")
            val tempTrieFile = File(context.cacheDir, "compile_${System.currentTimeMillis()}.trie")
            val tempBloomFile = File(context.cacheDir, "compile_${System.currentTimeMillis()}.bloom")

            try {
                Timber.d("Local compile: downloading $trimmedUrl")
                val response = client.get(trimmedUrl)
                val channel = response.bodyAsChannel()
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read > 0) output.write(buffer, 0, read)
                    }
                }

                val ruleCount =
                    tunnel.Tunnel.compileFilterList(
                        tempFile.absolutePath,
                        tempTrieFile.absolutePath,
                        tempBloomFile.absolutePath,
                    ).toInt()

                Timber.d("Local compile: $ruleCount rules")

                val finalName = displayName?.takeIf { it.isNotBlank() } ?: deriveFilterName(trimmedUrl)

                val filterEntity =
                    FilterList(
                        name = finalName,
                        url = trimmedUrl,
                        description = "Custom filter: $finalName",
                        isEnabled = true,
                        isBuiltIn = false,
                        category = FilterList.CATEGORY_AD,
                        ruleCount = ruleCount,
                        domainCount = ruleCount,
                        bloomUrl = "",
                        trieUrl = "",
                        cssUrl = "",
                        originalUrl = trimmedUrl,
                        lastUpdated = System.currentTimeMillis(),
                    )
                val insertedId = filterListDao.insert(filterEntity)

                val finalTrie = File(remoteFilterDir, "$insertedId.trie")
                val finalBloom = File(remoteFilterDir, "$insertedId.bloom")
                tempTrieFile.copyTo(finalTrie, overwrite = true)
                tempBloomFile.copyTo(finalBloom, overwrite = true)

                val updatedEntity =
                    filterEntity.copy(
                        id = insertedId,
                        bloomUrl = "local://$insertedId.bloom",
                        trieUrl = "local://$insertedId.trie",
                    )
                filterListDao.update(updatedEntity)

                Result.success(updatedEntity)
            } catch (e: Exception) {
                Timber.e(e, "Local compile failed")
                Result.failure(CustomFilterException("Local compile failed: ${e.message}", e))
            } finally {
                tempFile.delete()
                tempTrieFile.delete()
                tempBloomFile.delete()
            }
        }

    suspend fun updateLocally(filter: FilterList): Result<FilterList> {
        val url = filter.originalUrl.ifEmpty { filter.url }
        val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR).apply { mkdirs() }
        val tempFile = File(context.cacheDir, "filter_update_${System.currentTimeMillis()}.txt")

        return try {
            Timber.d("Local update: downloading $url")
            val response = client.get(url)
            val channel = response.bodyAsChannel()
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read > 0) output.write(buffer, 0, read)
                }
            }

            val triePath = File(remoteFilterDir, "${filter.id}.trie").absolutePath
            val bloomPath = File(remoteFilterDir, "${filter.id}.bloom").absolutePath

            val ruleCount =
                tunnel.Tunnel.compileFilterList(
                    tempFile.absolutePath,
                    triePath,
                    bloomPath,
                ).toInt()

            val updated =
                filter.copy(
                    ruleCount = ruleCount,
                    domainCount = ruleCount,
                    lastUpdated = System.currentTimeMillis(),
                )
            filterListDao.update(updated)

            Timber.d("Local update: ${filter.name}, rules=$ruleCount")
            Result.success(updated)
        } catch (e: Exception) {
            Timber.e(e, "Local update failed: ${filter.name}")
            Result.failure(CustomFilterException("Local update failed: ${e.message}", e))
        } finally {
            tempFile.delete()
        }
    }

    suspend fun recompileLocally(filter: FilterList): Result<FilterList> =
        withContext(Dispatchers.IO) {
            val url = filter.originalUrl.ifEmpty { filter.url }
            val remoteFilterDir = File(context.filesDir, REMOTE_FILTERS_DIR).apply { mkdirs() }
            val tempFile = File(context.cacheDir, "filter_recompile_${System.currentTimeMillis()}.txt")

            try {
                Timber.d("Recompile locally: downloading $url")
                val response = client.get(url)
                val channel = response.bodyAsChannel()
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read > 0) output.write(buffer, 0, read)
                    }
                }

                val triePath = File(remoteFilterDir, "${filter.id}.trie").absolutePath
                val bloomPath = File(remoteFilterDir, "${filter.id}.bloom").absolutePath

                val ruleCount =
                    tunnel.Tunnel.compileFilterList(
                        tempFile.absolutePath,
                        triePath,
                        bloomPath,
                    ).toInt()

                val updated =
                    filter.copy(
                        ruleCount = ruleCount,
                        domainCount = ruleCount,
                        bloomUrl = "local://${filter.id}.bloom",
                        trieUrl = "local://${filter.id}.trie",
                        lastUpdated = System.currentTimeMillis(),
                    )
                filterListDao.update(updated)

                Timber.d("Recompile locally: ${filter.name}, rules=$ruleCount")
                Result.success(updated)
            } catch (e: Exception) {
                Timber.e(e, "Recompile locally failed: ${filter.name}")
                Result.failure(CustomFilterException("Local recompile failed: ${e.message}", e))
            } finally {
                tempFile.delete()
            }
        }

    suspend fun enqueueLocalCompile(
        url: String,
        name: String,
    ) {
        val placeholder =
            FilterList(
                name = name,
                url = url,
                description = "Compiling…",
                isEnabled = true,
                isBuiltIn = false,
                category = FilterList.CATEGORY_AD,
                ruleCount = 0,
                domainCount = 0,
                bloomUrl = "local://pending.bloom",
                trieUrl = "local://pending.trie",
                originalUrl = url,
                lastUpdated = System.currentTimeMillis(),
            )
        val insertedId = filterListDao.insert(placeholder)
        Timber.d("Inserted placeholder filter id=$insertedId for: $name")

        val workRequest =
            OneTimeWorkRequestBuilder<FilterCompileWorker>()
                .setInputData(FilterCompileWorker.buildInputData(url, name, insertedId))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FilterCompileWorker.WORK_NAME_PREFIX + insertedId,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
        Timber.d("Enqueued local compile worker for: $name")
    }

    fun enqueueRecompileLocally(filter: FilterList) {
        val url = filter.originalUrl.ifEmpty { filter.url }
        val workRequest =
            OneTimeWorkRequestBuilder<FilterCompileWorker>()
                .setInputData(FilterCompileWorker.buildInputData(url, filter.name, filter.id))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FilterCompileWorker.WORK_NAME_PREFIX + filter.id,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
        Timber.d("Enqueued local recompile worker for: ${filter.name}")
    }
}
