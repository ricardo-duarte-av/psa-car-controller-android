package pt.aguiarvieira.psacc.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import pt.aguiarvieira.psacc.domain.model.CarCommand
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

    suspend fun send(vin: String, command: CarCommand): Result<Unit>

    /** PSACC only records trips for its first vehicle. */
    suspend fun trips(): Result<List<Trip>>
    suspend fun chargingSessions(vin: String): Result<List<ChargingSession>>
}
