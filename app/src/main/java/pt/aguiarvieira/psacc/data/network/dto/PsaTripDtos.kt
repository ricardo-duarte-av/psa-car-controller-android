package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /vehicles/<vin>/psa_trips` — the trips PSA recorded itself.
 *
 * Richer than the ones PSACC rebuilds from polled positions: energy levels at both ends for every
 * energy type, consumption, average and maximum speed, and start/stop positions when the car
 * reports its location. Fields use PSA's camelCase.
 */
@Serializable
data class PsaTripDto(
    val id: String? = null,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("stoppedAt") val stoppedAt: String? = null,
    /** Seconds, unlike PSACC's trips which are in minutes. */
    val duration: Double? = null,
    val distance: Double? = null,
    @SerialName("startMileage") val startMileage: Double? = null,
    @SerialName("startEnergies") val startEnergies: List<PsaTripEnergyDto>? = null,
    @SerialName("endEnergies") val endEnergies: List<PsaTripEnergyDto>? = null,
    @SerialName("energyConsumptions") val energyConsumptions: List<PsaTripConsumptionDto>? = null,
    val kinetic: PsaTripKineticDto? = null,
    @SerialName("startPosition") val startPosition: PositionDto? = null,
    @SerialName("stopPosition") val stopPosition: PositionDto? = null,
    /** False while the trip is being driven; [stoppedAt] is then PSA's latest update. */
    val done: Boolean? = null,
)

@Serializable
data class PsaTripEnergyDto(
    val type: String? = null,
    val level: Double? = null,
    val autonomy: Double? = null,
)

@Serializable
data class PsaTripConsumptionDto(
    val type: String? = null,
    /** Total used over the trip (kWh or litres, depending on [type]). */
    val consumption: Double? = null,
    /** Per 100 km. */
    @SerialName("avgConsumption") val avgConsumption: Double? = null,
)

@Serializable
data class PsaTripKineticDto(
    @SerialName("avgSpeed") val avgSpeed: Double? = null,
    @SerialName("maxSpeed") val maxSpeed: Double? = null,
)

/** `GET /vehicles/<vin>/maintenance` */
@Serializable
data class MaintenanceDto(
    @SerialName("daysBeforeMaintenance") val daysBefore: Double? = null,
    @SerialName("mileageBeforeMaintenance") val mileageBefore: Double? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
)
