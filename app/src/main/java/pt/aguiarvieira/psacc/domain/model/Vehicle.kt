package pt.aguiarvieira.psacc.domain.model

import java.time.Duration
import java.time.Instant

data class Vehicle(
    val vin: String,
    val label: String,
    val brand: String?,
    /** Usable battery capacity in kWh; 0/absent for pure ICE cars. */
    val batteryKwh: Double?,
    /** Tank size in litres; 0/absent for BEVs. */
    val fuelCapacityLitres: Double?,
) {
    val displayName: String get() = label.ifBlank { vin }
}

/** A snapshot of `get_vehicleinfo`, flattened into what the UI shows. */
data class VehicleStatus(
    val electric: ElectricEnergy?,
    val fuel: FuelEnergy?,
    val odometerKm: Double?,
    val outsideTempC: Double?,
    val isDay: Boolean?,
    val ignition: String?,
    val moving: Boolean?,
    val speed: Double?,
    val auxBatteryVoltage: Double?,
    val position: VehiclePosition?,
    val preconditioning: Preconditioning?,
    val doorLock: DoorLockState?,
    val openDoors: List<String>,
    val privacy: String?,
    val serviceType: String?,
    val updatedAt: Instant?,
)

data class ElectricEnergy(
    val levelPercent: Double?,
    val rangeKm: Double?,
    val batteryHealthPercent: Double?,
    val charging: ChargingState?,
    val updatedAt: Instant?,
)

data class FuelEnergy(
    val levelPercent: Double?,
    val rangeKm: Double?,
    val updatedAt: Instant?,
)

data class ChargingState(
    val status: ChargeStatus,
    val rawStatus: String?,
    val plugged: Boolean,
    /** "No", "Slow" or "Quick". */
    val mode: String?,
    /** km of range gained per hour. */
    val rateKmh: Double?,
    val remaining: Duration?,
    /** Delayed-charge start time as a duration after midnight (PSA's "PT22H30M"). */
    val scheduledStart: Duration?,
)

enum class ChargeStatus { InProgress, Stopped, Finished, Disconnected, Failure, Unknown;

    companion object {
        fun from(raw: String?): ChargeStatus = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: Unknown
    }
}

data class VehiclePosition(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val updatedAt: Instant?,
)

data class Preconditioning(
    val active: Boolean,
    val rawStatus: String?,
    val failureCause: String?,
    val updatedAt: Instant?,
)

enum class DoorLockState { Locked, Unlocked, Partial }

/** What PSACC's own charge controller (threshold + stop hour) is configured to do. */
data class ChargeControlSettings(
    val thresholdPercent: Int,
    /** Hour/minute at which PSACC stops charging, or null when disabled. */
    val stopAt: HourMinute?,
)

data class HourMinute(val hour: Int, val minute: Int) {
    override fun toString(): String = "%02d:%02d".format(hour, minute)
}

/** Remote commands PSACC relays to the car over PSA's MQTT service. */
sealed interface CarCommand {
    data object WakeUp : CarCommand
    data class Preconditioning(val on: Boolean) : CarCommand
    data class Lock(val locked: Boolean) : CarCommand
    data object Horn : CarCommand
    data object Lights : CarCommand
    data class Charge(val start: Boolean) : CarCommand
}
