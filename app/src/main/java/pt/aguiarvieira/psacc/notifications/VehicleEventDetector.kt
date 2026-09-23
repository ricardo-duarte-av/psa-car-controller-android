package pt.aguiarvieira.psacc.notifications

import kotlinx.serialization.Serializable
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus

/**
 * What the last background check saw for one vehicle, persisted between runs (each WorkManager run
 * may be a fresh process, so in-memory state would re-baseline forever and never notify).
 *
 * Each part has its own `*Baselined` flag: the first successful read of a part only records it, so
 * enabling notifications — or a trips request that failed last time — never replays history.
 */
@Serializable
data class VehicleWatch(
    val statusBaselined: Boolean = false,
    val ignitionOn: Boolean? = null,
    val chargeStatus: String? = null,
    val plugged: Boolean? = null,
    val tripsBaselined: Boolean = false,
    val lastTripStartMs: Long? = null,
    val sessionsBaselined: Boolean = false,
    val lastSessionStartMs: Long? = null,
    /** Start of a session that was still running last time, so its completion can be reported. */
    val openSessionStartMs: Long? = null,
)

/** Fresh data for one vehicle; a null part means that request failed and must not move the watch. */
data class VehicleSnapshot(
    val vehicle: Vehicle,
    val status: VehicleStatus?,
    val trips: List<Trip>?,
    val sessions: List<ChargingSession>?,
)

sealed interface VehicleEvent {
    val category: NotificationCategory

    data class TripRecorded(val trip: Trip) : VehicleEvent {
        override val category get() = NotificationCategory.TRIPS
    }

    data class IgnitionChanged(val on: Boolean, val status: VehicleStatus) : VehicleEvent {
        override val category get() = NotificationCategory.IGNITION
    }

    data class ChargingChanged(val to: ChargeStatus, val status: VehicleStatus) : VehicleEvent {
        override val category get() = NotificationCategory.CHARGING
    }

    data class PlugChanged(val plugged: Boolean, val status: VehicleStatus) : VehicleEvent {
        override val category get() = NotificationCategory.CHARGING
    }

    data class SessionFinished(val session: ChargingSession) : VehicleEvent {
        override val category get() = NotificationCategory.CHARGING_SESSIONS
    }
}

data class Detection(val events: List<VehicleEvent>, val watch: VehicleWatch)

object VehicleEventDetector {

    /** Beyond this many new trips in one check (e.g. after days offline) only the latest are posted. */
    const val MAX_TRIP_EVENTS = 5

    fun detect(previous: VehicleWatch?, snapshot: VehicleSnapshot): Detection {
        val prev = previous ?: VehicleWatch()
        val events = mutableListOf<VehicleEvent>()
        var watch = prev

        snapshot.status?.let { status ->
            val ignitionOn = ignitionOn(status.ignition)
            val charging = status.electric?.charging
            if (prev.statusBaselined) {
                if (prev.ignitionOn != null && ignitionOn != null && prev.ignitionOn != ignitionOn) {
                    events += VehicleEvent.IgnitionChanged(ignitionOn, status)
                }
                if (charging != null) {
                    if (prev.plugged != null && prev.plugged != charging.plugged) {
                        events += VehicleEvent.PlugChanged(charging.plugged, status)
                    }
                    val from = prev.chargeStatus?.let { ChargeStatus.from(it) }
                    if (from != null && from != charging.status && isReportable(from, charging.status)) {
                        events += VehicleEvent.ChargingChanged(charging.status, status)
                    }
                }
            }
            watch = watch.copy(
                statusBaselined = true,
                ignitionOn = ignitionOn ?: prev.ignitionOn,
                chargeStatus = charging?.status?.name ?: prev.chargeStatus,
                plugged = charging?.plugged ?: prev.plugged,
            )
        }

        snapshot.trips?.let { trips ->
            // A trip still being driven is reported (and the watch moved past it) once it's done.
            val starts = trips
                .filterNot { it.inProgress }
                .mapNotNull { t -> t.startAt?.toEpochMilli()?.let { it to t } }
            if (prev.tripsBaselined) {
                val newTrips = starts
                    .filter { (start, _) -> prev.lastTripStartMs == null || start > prev.lastTripStartMs }
                    .sortedBy { it.first }
                    .map { it.second }
                    .takeLast(MAX_TRIP_EVENTS)
                newTrips.forEach { events += VehicleEvent.TripRecorded(it) }
            }
            watch = watch.copy(
                tripsBaselined = true,
                lastTripStartMs = listOfNotNull(prev.lastTripStartMs, starts.maxOfOrNull { it.first }).maxOrNull(),
            )
        }

        snapshot.sessions?.let { sessions ->
            val byStart = sessions.mapNotNull { s -> s.startAt?.toEpochMilli()?.let { it to s } }
            if (prev.sessionsBaselined) {
                // A session that was running last time and has now stopped.
                prev.openSessionStartMs?.let { open ->
                    byStart.firstOrNull { it.first == open && !it.second.inProgress }
                        ?.let { events += VehicleEvent.SessionFinished(it.second) }
                }
                // Sessions that both started and finished since the last check.
                byStart
                    .filter { (start, s) -> (prev.lastSessionStartMs == null || start > prev.lastSessionStartMs) && !s.inProgress }
                    .sortedBy { it.first }
                    .forEach { events += VehicleEvent.SessionFinished(it.second) }
            }
            watch = watch.copy(
                sessionsBaselined = true,
                lastSessionStartMs = listOfNotNull(prev.lastSessionStartMs, byStart.maxOfOrNull { it.first }).maxOrNull(),
                openSessionStartMs = byStart.filter { it.second.inProgress }.maxOfOrNull { it.first },
            )
        }

        return Detection(events, watch)
    }

    /** PSA ignition types: Start/StartUp = on; Stop/Free (accessory) = off. */
    internal fun ignitionOn(raw: String?): Boolean? = when (raw?.lowercase()) {
        "start", "startup" -> true
        "stop", "free" -> false
        else -> null
    }

    /**
     * Status changes worth a notification. "Disconnected" is covered by the unplugged event, and
     * "Stopped" only matters when a charge was actually running (not e.g. Disconnected → Stopped on
     * plug-in while waiting for a scheduled start).
     */
    private fun isReportable(from: ChargeStatus, to: ChargeStatus): Boolean = when (to) {
        ChargeStatus.InProgress, ChargeStatus.Finished, ChargeStatus.Failure -> true
        ChargeStatus.Stopped -> from == ChargeStatus.InProgress
        ChargeStatus.Disconnected, ChargeStatus.Unknown -> false
    }
}
