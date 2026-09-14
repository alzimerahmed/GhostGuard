package app.ghostguard.ui.logs.data

import kotlinx.serialization.Serializable

@Serializable
enum class LogFilterStatus {
    ALL,
    BLOCKED,
    THREATS,
}
