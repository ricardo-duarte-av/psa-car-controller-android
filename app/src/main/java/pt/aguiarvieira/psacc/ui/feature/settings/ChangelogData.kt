package pt.aguiarvieira.psacc.ui.feature.settings

/**
 * One version's user-facing changes. Hand-written — NOT generated from commits.
 *
 * A [version] of `null` is the "Unreleased" bucket: changes committed to `main` but not yet cut into
 * a tagged release. When a release is tagged, its entries move under a new heading stamped with the
 * [version] and [date].
 */
data class ChangelogVersion(
    val version: String?,
    val date: String?, // dd/MM/yyyy
    val changes: List<String>,
)

/** Newest first. Unreleased (version = null) always on top. */
val CHANGELOG: List<ChangelogVersion> = listOf(
    ChangelogVersion(
        version = null,
        date = null,
        changes = emptyList(),
    ),
    ChangelogVersion(
        version = "0.1.5",
        date = "18/09/2026",
        changes = listOf(
            "The Car tab now updates the moment PSA itself reports a change — plugging in, " +
                "charging, locking, the car starting or moving — when the server is set up to " +
                "receive those, instead of waiting for the next refresh.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.4",
        date = "18/09/2026",
        changes = listOf(
            "Remote commands now report what the car actually did — \"Doors unlocked\", or why PSA " +
                "refused — instead of only saying the command was sent. Needs a server that reports " +
                "command results; with an older one the app still says \"sent to the car\".",
            "A control PSA refuses for your car is greyed out as \"not available for this car\". " +
                "Settings → Reset unavailable controls tries them again.",
            "The Car tab now follows the server's live event stream while it is open, so the battery " +
                "level and range update within seconds instead of once a minute.",
            "The car's location is marked as \"last known\" when it hasn't been updated for a day.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.3",
        date = "16/09/2026",
        changes = listOf(
            "Notifications: turn on background checks in Settings to be notified about new trips, " +
                "the car being started or turned off, charging starting, finishing or stopping, plugging " +
                "in or unplugging, and recorded charging sessions. Each kind can be switched off, and " +
                "tapping a notification opens the matching tab.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.2",
        date = "16/09/2026",
        changes = listOf(
            "Trips where the car didn't report any GPS movement now say no route was recorded, " +
                "instead of showing an empty map.",
            "The tiles in the Car tab's Status section are now all the same size.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.1",
        date = "16/09/2026",
        changes = listOf(
            "Maps: the car's location and each trip's route are now shown on a Google map. Tap a " +
                "map to open it full screen.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.0",
        date = "16/09/2026",
        changes = listOf(
            "First version: connect to your PSA Car Controller server with its address and HTTP login.",
            "Car dashboard with battery and fuel levels, range, charging state, odometer, " +
                "temperature, doors and the car's last location.",
            "Remote controls: lock and unlock, climate, horn, lights and asking the car for fresh data.",
            "Charging controls: start or stop a charge, set the scheduled start time, and set a " +
                "charge limit and stop time when charge control is enabled on the server.",
            "Trips history with consumption and a sketch of each route.",
            "Charging history with energy added and cost.",
        ),
    ),
)
