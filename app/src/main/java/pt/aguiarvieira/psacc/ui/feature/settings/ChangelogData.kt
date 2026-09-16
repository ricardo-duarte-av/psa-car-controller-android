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
