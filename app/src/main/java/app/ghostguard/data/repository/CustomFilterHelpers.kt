package app.ghostguard.data.repository

internal data class FilterInfo(
    val name: String,
    val url: String,
    val ruleCount: Int,
    val updatedAt: String,
)

internal fun parseInfoJson(json: String): FilterInfo {
    fun extractString(key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    fun extractInt(key: String): Int {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    return FilterInfo(
        name = extractString("name") ?: "Custom Filter",
        url = extractString("url") ?: "",
        ruleCount = extractInt("ruleCount"),
        updatedAt = extractString("updatedAt") ?: "",
    )
}

internal fun deriveFilterName(url: String): String {
    return try {
        val path = url.substringAfterLast("/").substringBeforeLast(".")
        if (path.isNotBlank()) {
            path.replace(Regex("[^a-zA-Z0-9_-]"), " ")
                .trim()
                .replaceFirstChar { it.uppercase() }
        } else {
            "Custom Filter"
        }
    } catch (_: Exception) {
        "Custom Filter"
    }
}

internal fun sanitizeName(url: String): String {
    return url.substringAfterLast("/")
        .substringBeforeLast(".")
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .take(64)
        .ifBlank { "custom_${System.currentTimeMillis()}" }
}
