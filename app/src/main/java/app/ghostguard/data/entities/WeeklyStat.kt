package app.ghostguard.data.entities

data class WeeklyStat(
    val week: String,
    val total: Int,
    val blocked: Int,
)
