package pt.aguiarvieira.psacc.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
object Routes {
    /** First run (or after disconnecting): server URL + basic-auth credentials. */
    @Serializable
    object Connect

    /** The tabbed shell: Car / Trips / Charging. */
    @Serializable
    object Home

    @Serializable
    object Settings

    @Serializable
    object Changelog

    @Serializable
    object About
}
