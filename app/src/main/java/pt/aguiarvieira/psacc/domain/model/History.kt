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
    /** Battery % at the start and the end, when the server reports PSA's own trips. */
    val startBatteryPercent: Double? = null,
    val endBatteryPercent: Double? = null,
    val startFuelPercent: Double? = null,
    val endFuelPercent: Double? = null,
    val maxSpeed: Double? = null,
    /** Litres actually burnt over the trip, when PSA reports them. */
    val fuelLitres: Double? = null,
    /** From the forked daemon's merged trips, which exist per vehicle. */
    val merged: Boolean = false,
    /** Still being driven: [endAt], the distance and the consumptions are the ones so far. */
    val inProgress: Boolean = false,
) {
    val endAt: Instant? get() = if (startAt != null && duration != null) startAt.plus(duration) else null

    /**
     * True when the GPS trace has at least two distinct points (~1 m apart). PSA sometimes keeps
     * reporting a stale position while the car drives, giving a trip whose samples all coincide.
     */
    val hasRoute: Boolean
        get() = route.distinctBy { (it.latitude * 1e5).toLong() to (it.longitude * 1e5).toLong() }.size >= 2
}

data class LatLng(val latitude: Double, val longitude: Double)

/** Distance and days before the next service, from PSA. */
data class Maintenance(
    val daysBefore: Int?,
    val distanceBefore: Double?,
    val updatedAt: java.time.Instant?,
)

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
    val place: ChargePlace = ChargePlace.Home,
    /** The kWh the charger billed, set by hand; [kwh] is PSACC's estimate from the battery levels. */
    val meteredKwh: Double? = null,
    /** [price] was set by hand rather than estimated. */
    val priceManual: Boolean = false,
) {
    val duration: Duration? get() = if (startAt != null && stopAt != null) Duration.between(startAt, stopAt) else null
    val inProgress: Boolean get() = stopAt == null

    /** The best known energy: the charger's when set by hand, else the estimate. */
    val energy: Double? get() = meteredKwh ?: kwh
    val edited: Boolean get() = priceManual || meteredKwh != null
}

/** What can be set by hand on a finished session; a null [price] goes back to PSACC's estimate. */
data class ChargingSessionEdit(
    val place: ChargePlace,
    val meteredKwh: Double?,
    val price: Double?,
)

/** Where a charge happened, set by hand; a session is at [Home] until told otherwise. */
enum class ChargePlace(val apiValue: String, val label: String) {
    Home("home", "Home"),
    Work("work", "Work"),
    Public("public", "Public"),
    ;

    companion object {
        fun fromApi(value: String?): ChargePlace = entries.firstOrNull { it.apiValue == value } ?: Home
    }
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
