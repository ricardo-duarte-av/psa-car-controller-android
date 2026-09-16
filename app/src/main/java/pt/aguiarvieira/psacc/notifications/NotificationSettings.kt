package pt.aguiarvieira.psacc.notifications

/** User choices for background checks. Off by default: polling is opt-in. */
data class NotificationSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val trips: Boolean = true,
    val ignition: Boolean = true,
    val charging: Boolean = true,
    val chargingSessions: Boolean = true,
) {
    fun allows(category: NotificationCategory): Boolean = when (category) {
        NotificationCategory.TRIPS -> trips
        NotificationCategory.IGNITION -> ignition
        NotificationCategory.CHARGING -> charging
        NotificationCategory.CHARGING_SESSIONS -> chargingSessions
    }

    companion object {
        /** WorkManager's periodic floor is 15 minutes; shorter periods are silently raised. */
        const val DEFAULT_INTERVAL_MINUTES = 15
        val INTERVAL_OPTIONS = listOf(15, 30, 60, 180)
    }
}

/** One Android notification channel per category, so each can also be tuned in system settings. */
enum class NotificationCategory(val channelId: String, val title: String, val description: String) {
    TRIPS("trips", "New trips", "A trip was recorded"),
    IGNITION("ignition", "Ignition", "The car was started or turned off"),
    CHARGING("charging", "Charging status", "Charging started, finished, stopped or failed; plugged in or unplugged"),
    CHARGING_SESSIONS("charging_sessions", "Charging sessions", "A charging session was recorded, with energy and cost"),
}

/** Outcome of the most recent background check, shown in Settings. */
data class LastCheck(val atMillis: Long, val error: String?)
