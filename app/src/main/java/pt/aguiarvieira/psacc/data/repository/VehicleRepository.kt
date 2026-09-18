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
import pt.aguiarvieira.psacc.domain.model.HourMinute
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus

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

    /** PSACC only records trips for its first vehicle. */
    suspend fun trips(): Result<List<Trip>>
    suspend fun chargingSessions(vin: String): Result<List<ChargingSession>>
}
