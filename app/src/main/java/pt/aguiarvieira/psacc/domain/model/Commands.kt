package pt.aguiarvieira.psacc.domain.model

import java.time.Instant

/**
 * What became of a remote command.
 *
 * PSA relays commands asynchronously: the daemon answers as soon as the MQTT message is queued and
 * the car's real answer arrives seconds later. The forked daemon keeps that answer (`/command/<id>`,
 * `?wait=`), so [Pending] can still resolve into [Success] or [Failed]; the stock daemon can only
 * ever report [Sent].
 */
enum class CommandState {
    /** Stock daemon: queued, and nothing more will ever be known. */
    Sent,
    Pending,
    Success,
    Failed,
}

data class CommandOutcome(
    val state: CommandState,
    val message: String,
    val correlationId: String? = null,
    val action: String? = null,
    val returnCode: String? = null,
    val reason: String? = null,
    val updatedAt: Instant? = null,
) {
    val settled: Boolean get() = state == CommandState.Success || state == CommandState.Failed

    /**
     * PSA rejected the command outright (no service key for this car, not eligible...). Retrying is
     * pointless, so the app remembers it and greys the control out.
     */
    val refused: Boolean
        get() = state == CommandState.Failed && (
            reason?.let { r -> REFUSAL_REASONS.any { r.contains(it, ignoreCase = true) } } == true ||
                message.startsWith("PSA refused", ignoreCase = true)
            )

    private companion object {
        val REFUSAL_REASONS = listOf(
            "no.matching.service.key",
            "service.not.available",
            "not.allowed",
            "vehicle.not.eligible",
            "invalid.request",
        )
    }
}

/** Which remote commands this server can report results for, and whether it streams events. */
data class ServerCapabilities(
    val commandResults: Boolean = false,
    val events: Boolean = false,
) {
    companion object {
        /** Plain upstream PSA Car Controller: commands are fire-and-forget, no event stream. */
        val Basic = ServerCapabilities()
    }
}

/** Stable key for remembering that PSA refuses a command for a given car. */
fun CarCommand.key(): String = when (this) {
    CarCommand.WakeUp -> "wakeup"
    is CarCommand.Preconditioning -> "preconditioning"
    is CarCommand.Lock -> "lock_door"
    CarCommand.Horn -> "horn"
    CarCommand.Lights -> "lights"
    is CarCommand.Charge -> "charge"
}

/** Live data pushed by the daemon's `/events` stream. */
sealed interface PsaccEvent {
    /**
     * A vehicle event straight from PSA's MQTT feed.
     *
     * Only [batteryLevel] and [autonomy] are corroborated by the API status; [cablePlugged] has been
     * seen true on an unplugged car and [charging] is derived from the charge rate, so the UI keeps
     * taking plug/charge state from the API status. The values are parsed anyway so that confirming
     * them later is a UI change, not a protocol change.
     */
    data class VehicleUpdate(
        val vin: String?,
        val at: Instant?,
        val batteryLevel: Double?,
        val autonomy: Double?,
        val charging: Boolean?,
        val chargingRate: Double?,
        val cablePlugged: Boolean?,
        val preconditioning: Boolean?,
    ) : PsaccEvent

    /** A command's result, arriving without having to poll `/command/<id>`. */
    data class CommandUpdate(val outcome: CommandOutcome) : PsaccEvent
}

/** Matches the `action` the daemon reports (its MQTT topic) back to the command that caused it. */
fun CarCommand.matchesAction(action: String): Boolean = when (this) {
    CarCommand.WakeUp -> action.contains("VehCharge/state", ignoreCase = true)
    is CarCommand.Preconditioning -> action.contains("ThermalPrecond", ignoreCase = true)
    is CarCommand.Lock -> action.contains("Doors", ignoreCase = true)
    CarCommand.Horn -> action.contains("Horn", ignoreCase = true)
    CarCommand.Lights -> action.contains("Lights", ignoreCase = true)
    is CarCommand.Charge -> action.equals("VehCharge", ignoreCase = true)
}
