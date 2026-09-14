package app.ghostguard.data.repository

/**
 * Status of filter-list updates, surfaced to the UI (audit finding H3).
 *
 * When a built-in filter update fails Ed25519/SHA-256 verification, the update
 * is rejected, the previously-installed version is kept, and
 * [VerificationFailed] is emitted so the user can see the list was NOT updated.
 */
sealed class FilterUpdateStatus {
    data object Idle : FilterUpdateStatus()

    data class Success(
        val timestamp: Long,
    ) : FilterUpdateStatus()

    data class VerificationFailed(
        val filterName: String,
        val reason: String,
        val timestamp: Long,
    ) : FilterUpdateStatus()
}
