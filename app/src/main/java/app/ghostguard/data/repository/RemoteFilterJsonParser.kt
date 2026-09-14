package app.ghostguard.data.repository

import timber.log.Timber

internal fun parseRemoteFilterJson(json: String): List<app.ghostguard.data.remote.models.FilterList> {
    return try {
        val results = mutableListOf<app.ghostguard.data.remote.models.FilterList>()
        val objects =
            json.split("},").map {
                it
                    .trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .trim() + "}"
            }

        for (obj in objects) {
            val cleaned =
                obj
                    .trim()
                    .removePrefix("{")
                    .removeSuffix("}")
                    .removeSuffix("},")

            fun extractString(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex()
                return pattern
                    .find(cleaned)
                    ?.groupValues
                    ?.get(1)
                    ?.replace("\\u0026", "&")
            }

            fun extractInt(key: String): Int {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern
                    .find(cleaned)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
            }

            fun extractBoolean(key: String): Boolean {
                val pattern = "\"$key\"\\s*:\\s*(true|false)".toRegex()
                return pattern.find(cleaned)?.groupValues?.get(1) == "true"
            }

            val name = extractString("name")
            val bloomUrl = extractString("bloomUrl")
            val trieUrl = extractString("trieUrl")
            if (name == null || bloomUrl == null || trieUrl == null) continue

            results.add(
                app.ghostguard.data.remote.models.FilterList(
                    name = name,
                    id = extractString("id") ?: name.lowercase().replace(" ", "_"),
                    description = extractString("description"),
                    isEnabled = extractBoolean("isEnabled"),
                    isBuiltIn = extractBoolean("isBuiltIn"),
                    category = extractString("category"),
                    ruleCount = extractInt("ruleCount"),
                    bloomUrl = bloomUrl,
                    trieUrl = trieUrl,
                    cssUrl = extractString("cssUrl"),
                    scriptletsUrl = extractString("scriptletsUrl"),
                    originalUrl = extractString("originalUrl"),
                ),
            )
        }
        results
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse remote filter JSON")
        emptyList()
    }
}
