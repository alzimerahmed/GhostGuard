package app.ghostguard.data.repository

import android.content.Context
import app.ghostguard.data.entities.FilterList
import app.ghostguard.data.remote.FilterDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class CosmeticRuleCompiler(
    private val context: Context,
    private val downloadManager: FilterDownloadManager,
) {
    companion object {
        private const val CACHE_DIR = "filter_cache"
    }

    suspend fun compileScriptletRules(enabledLists: List<FilterList>) =
        withContext(Dispatchers.IO) {
            try {
                val validLists = enabledLists.filter { it.category != FilterList.CATEGORY_SECURITY }
                if (validLists.isEmpty()) {
                    File(context.filesDir, "$CACHE_DIR/scriptlets_combined.txt").delete()
                    return@withContext
                }

                val sb = StringBuilder()
                var added = 0
                for (filter in validLists) {
                    if (filter.scriptletsUrl.isEmpty()) continue
                    val f = File(context.filesDir, "remote_filters/${filter.id}.scriptlets")
                    if (f.exists() && f.length() > 0) {
                        sb.append(f.readText())
                        if (!sb.endsWith("\n")) sb.append("\n")
                        added++
                    }
                }

                val outFile = File(context.filesDir, "$CACHE_DIR/scriptlets_combined.txt")
                if (added > 0) {
                    outFile.parentFile?.mkdirs()
                    outFile.writeText(sb.toString())
                    Timber.d("Wrote scriptlets ($added lists, ${sb.length} bytes)")
                } else {
                    outFile.delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to compile scriptlet rules")
            }
        }

    suspend fun compileCosmeticRules(enabledLists: List<FilterList>) =
        withContext(Dispatchers.IO) {
            try {
                val validLists = enabledLists.filter { it.category != FilterList.CATEGORY_SECURITY }
                if (validLists.isEmpty()) return@withContext

                val cssBuilder = StringBuilder()
                var rulesAdded = 0

                for (filter in validLists) {
                    if (filter.cssUrl.isEmpty()) continue
                    val cssFile = File(context.filesDir, "remote_filters/${filter.id}.css")
                    if (cssFile.exists() && cssFile.length() > 0) {
                        val cssSnippet = downloadManager.getInjectableCss(cssFile)
                        if (cssSnippet.isNotEmpty()) {
                            cssBuilder.append(cssSnippet)
                            rulesAdded++
                        }
                    }
                }

                if (rulesAdded > 0) {
                    val finalCssFile = File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css")
                    finalCssFile.parentFile?.mkdirs()
                    finalCssFile.writeText(cssBuilder.toString())
                    Timber.d("Wrote cosmetic CSS (${cssBuilder.length} bytes)")
                } else {
                    File(context.filesDir, "$CACHE_DIR/cosmetic_rules.css").delete()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to compile cosmetic rules")
            }
        }
}
