package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /vehicles/trips` element (`Trip.get_info()` upstream). Trips are for the first vehicle only. */
@Serializable
data class TripDto(
    val id: Int? = null,
    @SerialName("start_at") val startAt: String? = null,
    /** Minutes. */
    val duration: Double? = null,
    /** Length unit configured on the server (km by default). */
    val distance: Double? = null,
    val mileage: Double? = null,
    @SerialName("speed_average") val speedAverage: Double? = null,
    /** Total kWh used. */
    val consumption: Double? = null,
    /** kWh / 100 km. */
    @SerialName("consumption_km") val consumptionKm: Double? = null,
    /** L / 100 km (only on cars with a fuel tank). */
    @SerialName("consumption_fuel_km") val consumptionFuelKm: Double? = null,
    /** Mean outside temperature during the trip. */
    @SerialName("consumption_by_temp") val temperature: Double? = null,
    @SerialName("altitude_diff") val altitudeDiff: Double? = null,
    val positions: TripPositionsDto? = null,
)

@Serializable
data class TripPositionsDto(
    val lat: List<Double?> = emptyList(),
    val long: List<Double?> = emptyList(),
)

/** `GET /vehicles/chargings` element — a row of the `battery` table plus computed durations. */
@Serializable
data class ChargingSessionDto(
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("stop_at") val stopAt: String? = null,
    @SerialName("VIN") val vin: String? = null,
    @SerialName("start_level") val startLevel: Double? = null,
    @SerialName("end_level") val endLevel: Double? = null,
    val co2: Double? = null,
    val kw: Double? = null,
    val price: Double? = null,
    @SerialName("charging_mode") val chargingMode: String? = null,
    val mileage: Double? = null,
    @SerialName("duration_min") val durationMin: Double? = null,
)

/** `GET /settings` — PSACC's config.ini. Only the parts the app displays are modelled. */
@Serializable
data class ServerSettingsDto(
    @SerialName("General") val general: GeneralSettingsDto? = null,
    @SerialName("Electricity_config") val electricity: ElectricitySettingsDto? = null,
)

@Serializable
data class GeneralSettingsDto(
    val currency: String? = null,
    @SerialName("length_unit") val lengthUnit: String? = null,
)

@Serializable
data class ElectricitySettingsDto(
    @SerialName("day_price") val dayPrice: Double? = null,
    @SerialName("night_price") val nightPrice: Double? = null,
    @SerialName("night_hour_start") val nightHourStart: String? = null,
    @SerialName("night_hour_end") val nightHourEnd: String? = null,
    @SerialName("charger_efficiency") val chargerEfficiency: Double? = null,
)

/** `GET /battery/soh/<vin>` */
@Serializable
data class SohDto(val soh: Double? = null)
