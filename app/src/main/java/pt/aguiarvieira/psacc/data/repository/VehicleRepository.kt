package pt.aguiarvieira.psacc.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.CommandOutcome
import pt.aguiarvieira.psacc.domain.model.CommandState
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
import pt.aguiarvieira.psacc.domain.model.ServerCapabilities
import pt.aguiarvieira.psacc.domain.model.ChargeControlSettings
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ChargingSessionEdit
import pt.aguiarvieira.psacc.domain.model.HourMinute
import pt.aguiarvieira.psacc.domain.model.Maintenance
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import java.time.Instant

interface VehicleRepository {
    /** Vehicles known to PSACC; empty until [refreshVehicles] succeeds. */
    val vehicles: StateFlow<List<Vehicle>>

    /** The vehicle the app is showing: the persisted pick, else the first one. */
    val selectedVehicle: Flow<Vehicle?>

    /** Units and prices from PSACC's config, [ServerSettings.Default] until loaded. */
    val serverSettings: StateFlow<ServerSettings>

    suspend fun refreshVehicles(): Result<List<Vehicle>>

    /** Drops everything cached from the current server (on disconnect). */
    fun clear()
    suspend fun selectVehicle(vin: String)
    suspend fun refreshServerSettings(): Result<ServerSettings>

    /**
     * [fromCache] reads PSACC's last stored status (cheap); false makes PSACC query the PSA cloud now,
     * which is slower and counts against PSA's API quota.
     */
    suspend fun status(vin: String, fromCache: Boolean): Result<VehicleStatus>

    /** Battery state of health (%) recorded by PSACC, or null when it has none. */
    suspend fun batterySoh(vin: String): Result<Double?>

    /** Null when PSACC's charge control isn't set up for this car. */
    suspend fun chargeControl(vin: String): Result<ChargeControlSettings?>
    suspend fun setChargeThreshold(vin: String, percent: Int): Result<ChargeControlSettings?>
    suspend fun setChargeStop(vin: String, stopAt: HourMinute?): Result<ChargeControlSettings?>
    suspend fun setScheduledChargeStart(vin: String, at: HourMinute): Result<Unit>

    /**
     * Sends a remote command. On a daemon that reports results, waits up to [waitSeconds] for the
     * car's answer; otherwise returns [CommandState.Sent] as soon as it is queued.
     */
    suspend fun send(vin: String, command: CarCommand, waitSeconds: Int = 0): Result<CommandOutcome>

    /** Re-reads a pending command's result; null when the server has never heard of it. */
    suspend fun commandResult(correlationId: String): Result<CommandOutcome?>

    /** What this server supports; probed once per connection, [ServerCapabilities.Basic] until then. */
    val capabilities: StateFlow<ServerCapabilities>

    suspend fun refreshCapabilities(): ServerCapabilities

    /** Live events from the daemon's stream. Fails when the connection drops; callers retry. */
    fun events(): Flow<PsaccEvent>

    /**
     * The car's last live reading for [vin], which the daemon replays to every new `/events`
     * subscriber; null on a stock server, when none has arrived since the daemon started, or when
     * the stream can't be read. Needs [refreshCapabilities] to have run.
     */
    suspend fun lastVehicleUpdate(vin: String): PsaccEvent.VehicleUpdate?

    /**
     * The forked daemon's merged trips when it serves them (PSA's trips enriched with PSACC's battery
     * levels, route and temperature), else PSA's own trips (per vehicle, with the battery and fuel
     * levels at both ends), falling back to the ones PSACC rebuilds — which exist for its first car only.
     */
    suspend fun trips(vin: String?): Result<List<Trip>>

    /** Distance and days before the next service; null when the server doesn't serve it. */
    suspend fun maintenance(vin: String): Result<Maintenance?>

    /** Absolute URLs (on the daemon) of the car's pictures; empty when the server serves none. */
    suspend fun pictures(vin: String): Result<List<String>>
    suspend fun chargingSessions(vin: String): Result<List<ChargingSession>>

    /** Saves what was set by hand on a finished session (fork 0.1.28 on) and returns the session as saved. */
    suspend fun editChargingSession(vin: String, startAt: Instant, edit: ChargingSessionEdit): Result<ChargingSession>
}
