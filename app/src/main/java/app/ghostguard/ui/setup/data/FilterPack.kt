package app.ghostguard.ui.setup.data

/**
 * Curated filter pack presets offered by the one-tap setup wizard.
 *
 * Each preset maps to a set of existing built-in filter list names (as seeded
 * by [app.ghostguard.data.repository.FilterListRepository]). Names are matched
 * exactly against the DB; missing lists are skipped gracefully so the preset
 * still applies if the remote catalog changes.
 */
enum class FilterPack(
    val id: String,
    val filterListNames: Set<String>,
) {
    /** Lightweight — fewest false positives, core ad blocking only. */
    MINIMAL(
        id = "minimal",
        filterListNames =
            setOf(
                "Hagezi Light",
                "AdGuard Mobile Ads",
            ),
    ),

    /** Recommended default — solid ad, tracker and mobile-ad coverage. */
    BALANCED(
        id = "balanced",
        filterListNames =
            setOf(
                "EasyList",
                "EasyPrivacy",
                "AdGuard DNS",
                "AdGuard Mobile Ads",
                "Hagezi Normal",
            ),
    ),

    /** Maximum blocking — aggressive lists including threat intelligence. */
    STRICT(
        id = "strict",
        filterListNames =
            setOf(
                "EasyList",
                "EasyPrivacy",
                "AdGuard DNS",
                "AdGuard Mobile Ads",
                "Hagezi Pro++",
                "Hagezi TIF",
                "URLhaus Malicious URL Blocklist",
                "Peter Lowe's Ad and tracking server list",
            ),
    ),
    ;

    companion object {
        fun fromId(id: String): FilterPack = entries.firstOrNull { it.id == id } ?: BALANCED

        /** Number of preset lists that exist in [availableNames]. */
        fun matchedCount(
            pack: FilterPack,
            availableNames: Set<String>,
        ): Int = pack.filterListNames.count { it in availableNames }
    }
}
