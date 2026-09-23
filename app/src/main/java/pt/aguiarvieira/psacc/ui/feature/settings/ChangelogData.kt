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
        version = "0.1.20",
        date = "23/09/2026",
        changes = listOf(
            "With server 0.1.20: no more 0 km trips from switching the car on and off, and a drive " +
                "with a stop of under 5 minutes shows as one trip.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.19",
        date = "23/09/2026",
        changes = listOf(
            "A trip still being driven shows as in progress instead of as a finished trip.",
            "Trip notifications: one when a trip starts, replaced by one with the final distance and " +
                "consumption once the car stops.",
            "The app's version now follows the server's (PSACC fork 0.1.19).",
        ),
    ),
    ChangelogVersion(
        version = "0.1.11",
        date = "23/09/2026",
        changes = listOf(
            "Trips use the server's merged trips when it has them: PSA's own trips with the battery " +
                "levels, energy used, route and temperature the server recorded during them.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.10",
        date = "23/09/2026",
        changes = listOf(
            "Trips no longer show a full battery when PSA reports one with no electric range " +
                "(it does this when the battery is empty); the battery level is left out instead.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.9",
        date = "23/09/2026",
        changes = listOf(
            "Charging and plug notifications show the same battery level as the Car tab (the car's " +
                "live reading) instead of PSA's summary, which could say 100% on a nearly empty battery.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.8",
        date = "21/09/2026",
        changes = listOf(
            "The battery level and range on the Car tab now come from the car's live reading, which " +
                "is more reliable than PSA's summary (which sometimes showed 100% on an empty battery).",
            "Trips can show an estimated fuel cost: set a petrol price per litre in Settings " +
                "(kept only on your device).",
        ),
    ),
    ChangelogVersion(
        version = "0.1.7",
        date = "21/09/2026",
        changes = listOf(
            "Fixed the trip figures: fuel consumption and average speed were shown in the wrong " +
                "units (a 59 km/h trip read as 17 km/h, and consumption was 100 times too high).",
            "A trip only shows a battery or fuel level for the end of the trip when the car " +
                "actually reports one.",
            "Opening the app now refreshes the car by itself when the data is more than five " +
                "minutes old, instead of waiting for a pull to refresh.",
            "The Car tab now shows photos of your car — the official renders in its real colour and " +
                "trim — as a swipeable gallery.",
        ),
    ),
    ChangelogVersion(
        version = "0.1.6",
        date = "18/09/2026",
        changes = listOf(
            "Trips now come from PSA itself where the server offers them: many more trips, for " +
                "every car rather than just the first, each with the battery and fuel level before " +
                "and after, top speed, and the start and end locations when the car reports them.",
            "The Car tab shows when the next service is due.",
            "Lock and unlock are now greyed out on cars that never report their doors to PSA, " +
                "instead of failing when you press them.",
        ),
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
