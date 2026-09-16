package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire models for PSA Car Controller's Flask API (web/view/api.py upstream).
 *
 * PSACC serialises Python objects with `json.dumps(..., default=str)` / `jsonify`, so:
 *  - almost every field can be null, and whole sub-objects vanish when the car doesn't report them;
 *  - numbers that are integers on the PSA side often arrive as floats (`"level": 75.0`), so every
 *    numeric field is a Double;
 *  - datetimes are strings in several formats (see util/PsaccTime).
 * Keep everything nullable with defaults and let the Json instance ignore unknown keys.
 */

/** `GET /get_vehicles` element. */
@Serializable
data class VehicleDto(
    val vin: String,
    val label: String? = null,
    val brand: String? = null,
    @SerialName("battery_power") val batteryPower: Double? = null,
    @SerialName("fuel_capacity") val fuelCapacity: Double? = null,
    @SerialName("max_elec_consumption") val maxElecConsumption: Double? = null,
    @SerialName("max_fuel_consumption") val maxFuelConsumption: Double? = null,
)

/** `GET /get_vehicleinfo/<vin>` — the PSA connected-car "status" object. */
@Serializable
data class VehicleStatusDto(
    val battery: AuxBatteryDto? = null,
    @SerialName("doors_state") val doorsState: DoorsStateDto? = null,
    val energy: List<EnergyDto>? = null,
    val environment: EnvironmentDto? = null,
    val ignition: IgnitionDto? = null,
    val kinetic: KineticDto? = null,
    @SerialName("last_position") val lastPosition: PositionDto? = null,
    // Sic: PSA's API spells it with a double "n".
    @SerialName("preconditionning") val preconditioning: PreconditioningDto? = null,
    val privacy: PrivacyDto? = null,
    val safety: SafetyDto? = null,
    val service: ServiceDto? = null,
    @SerialName("timed_odometer") val odometer: OdometerDto? = null,
)

/** The 12 V auxiliary battery. */
@Serializable
data class AuxBatteryDto(
    val current: Double? = null,
    val voltage: Double? = null,
)

@Serializable
data class DoorsStateDto(
    @SerialName("locked_state") val lockedState: List<String>? = null,
    val opening: List<DoorOpeningDto>? = null,
)

@Serializable
data class DoorOpeningDto(
    val identifier: String? = null,
    val state: String? = null,
)

@Serializable
data class EnergyDto(
    @SerialName("updated_at") val updatedAt: String? = null,
    val autonomy: Double? = null,
    val battery: EnergyBatteryDto? = null,
    val charging: ChargingDto? = null,
    val consumption: Double? = null,
    val level: Double? = null,
    val residual: Double? = null,
    val type: String? = null,
)

@Serializable
data class EnergyBatteryDto(
    val capacity: Double? = null,
    val health: BatteryHealthDto? = null,
)

@Serializable
data class BatteryHealthDto(
    val capacity: Double? = null,
    val resistance: Double? = null,
)

@Serializable
data class ChargingDto(
    @SerialName("charging_mode") val chargingMode: String? = null,
    @SerialName("charging_rate") val chargingRate: Double? = null,
    @SerialName("next_delayed_time") val nextDelayedTime: String? = null,
    val plugged: Boolean? = null,
    @SerialName("remaining_time") val remainingTime: String? = null,
    val status: String? = null,
)

@Serializable
data class EnvironmentDto(
    val air: AirDto? = null,
    val luminosity: LuminosityDto? = null,
)

@Serializable
data class AirDto(val temp: Double? = null)

@Serializable
data class LuminosityDto(val day: Boolean? = null)

@Serializable
data class IgnitionDto(val type: String? = null)

@Serializable
data class KineticDto(
    val moving: Boolean? = null,
    val speed: Double? = null,
)

@Serializable
data class PositionDto(
    val geometry: GeometryDto? = null,
    val properties: PositionPropertiesDto? = null,
)

@Serializable
data class GeometryDto(
    /** GeoJSON order: longitude, latitude, optional altitude. */
    val coordinates: List<Double>? = null,
)

@Serializable
data class PositionPropertiesDto(
    val heading: Double? = null,
    @SerialName("signal_quality") val signalQuality: Double? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PreconditioningDto(
    @SerialName("air_conditioning") val airConditioning: AirConditioningDto? = null,
)

@Serializable
data class AirConditioningDto(
    val status: String? = null,
    @SerialName("failure_cause") val failureCause: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PrivacyDto(val state: String? = null)

@Serializable
data class SafetyDto(
    @SerialName("belt_warning") val beltWarning: String? = null,
)

@Serializable
data class ServiceDto(val type: String? = null)

@Serializable
data class OdometerDto(
    @SerialName("updated_at") val updatedAt: String? = null,
    val mileage: Double? = null,
)
