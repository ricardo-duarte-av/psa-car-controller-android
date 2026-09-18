package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/command/<id>`, `/commands` and the command endpoints of the forked daemon. */
@Serializable
data class CommandResultDto(
    @SerialName("correlation_id") val correlationId: String? = null,
    val vin: String? = null,
    val action: String? = null,
    val status: String? = null,
    @SerialName("return_code") val returnCode: String? = null,
    val reason: String? = null,
    val message: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** PSACC also answers `{"error": "..."}` with HTTP 200 (rate limits). */
    val error: String? = null,
)

/** The `data` payload of a `vehicle` server-sent event. */
@Serializable
data class VehicleEventDto(
    val vin: String? = null,
    val date: String? = null,
    @SerialName("battery_level") val batteryLevel: Double? = null,
    val autonomy: Double? = null,
    val charging: Boolean? = null,
    @SerialName("charging_rate") val chargingRate: Double? = null,
    @SerialName("cable_plugged") val cablePlugged: Boolean? = null,
    val preconditioning: Boolean? = null,
)

/** Envelope of every server-sent event: `{"type": ..., "date": ..., "data": {...}}`. */
@Serializable
data class SseEnvelopeDto<T>(
    val type: String? = null,
    val date: String? = null,
    val data: T? = null,
)
