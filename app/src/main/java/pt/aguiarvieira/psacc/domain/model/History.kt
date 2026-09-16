package pt.aguiarvieira.psacc.domain.model

import java.time.Duration
import java.time.Instant

data class Trip(
    val id: Int,
    val startAt: Instant?,
    val duration: Duration?,
    val distance: Double?,
    val odometer: Double?,
    val averageSpeed: Double?,
    val energyKwh: Double?,
    val kwhPer100: Double?,
    val litresPer100: Double?,
    val temperatureC: Double?,
    val altitudeDiff: Double?,
    val route: List<LatLng>,
) {
    val endAt: Instant? get() = if (startAt != null && duration != null) startAt.plus(duration) else null
}

data class LatLng(val latitude: Double, val longitude: Double)

data class ChargingSession(
    val startAt: Instant?,
    val stopAt: Instant?,
    val startLevel: Double?,
    val endLevel: Double?,
    val kwh: Double?,
    val price: Double?,
    val co2: Double?,
    val mode: String?,
    val odometer: Double?,
) {
    val duration: Duration? get() = if (startAt != null && stopAt != null) Duration.between(startAt, stopAt) else null
    val inProgress: Boolean get() = stopAt == null
}

/** Display-relevant parts of PSACC's config.ini. */
data class ServerSettings(
    val currency: String,
    val lengthUnit: String,
    val dayPrice: Double?,
    val nightPrice: Double?,
    val nightStart: String?,
    val nightEnd: String?,
    val chargerEfficiency: Double?,
) {
    companion object {
        val Default = ServerSettings(
            currency = "€",
            lengthUnit = "km",
            dayPrice = null,
            nightPrice = null,
            nightStart = null,
            nightEnd = null,
            chargerEfficiency = null,
        )
    }
}
