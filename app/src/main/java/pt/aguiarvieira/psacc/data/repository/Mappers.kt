package pt.aguiarvieira.psacc.data.repository

import pt.aguiarvieira.psacc.data.network.dto.ChargingSessionDto
import pt.aguiarvieira.psacc.data.network.dto.ServerSettingsDto
import pt.aguiarvieira.psacc.data.network.dto.TripDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleStatusDto
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ChargingState
import pt.aguiarvieira.psacc.domain.model.DoorLockState
import pt.aguiarvieira.psacc.domain.model.ElectricEnergy
import pt.aguiarvieira.psacc.domain.model.FuelEnergy
import pt.aguiarvieira.psacc.domain.model.LatLng
import pt.aguiarvieira.psacc.domain.model.Preconditioning
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehiclePosition
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.util.PsaccTime
import java.time.Duration

private val INACTIVE_PRECONDITIONING = listOf("Disabled", "Finished", "Failure")

internal fun VehicleDto.toDomain() = Vehicle(
    vin = vin,
    label = label.orEmpty(),
    brand = brand,
    batteryKwh = batteryPower,
    fuelCapacityLitres = fuelCapacity,
)

internal fun VehicleStatusDto.toDomain(): VehicleStatus {
    val electricDto = energy?.firstOrNull { it.type.equals("Electric", ignoreCase = true) }
    val fuelDto = energy?.firstOrNull { it.type.equals("Fuel", ignoreCase = true) }

    val electric = electricDto?.let { e ->
        ElectricEnergy(
            levelPercent = e.level,
            rangeKm = e.autonomy,
            // PSA reports 0 when unknown; hide it rather than claim a dead battery.
            batteryHealthPercent = e.battery?.health?.capacity?.takeIf { it > 0 },
            charging = e.charging?.let { c ->
                ChargingState(
                    status = ChargeStatus.from(c.status),
                    rawStatus = c.status,
                    plugged = c.plugged == true,
                    mode = c.chargingMode,
                    rateKmh = c.chargingRate,
                    remaining = PsaccTime.parseDuration(c.remainingTime),
                    scheduledStart = PsaccTime.parseDuration(c.nextDelayedTime),
                )
            },
            updatedAt = PsaccTime.parseInstant(e.updatedAt),
        )
    }
    val fuel = fuelDto?.let { f ->
        FuelEnergy(
            levelPercent = f.level,
            rangeKm = f.autonomy,
            updatedAt = PsaccTime.parseInstant(f.updatedAt),
        )
    }

    val coords = lastPosition?.geometry?.coordinates
    val position = if (coords != null && coords.size >= 2) {
        VehiclePosition(
            latitude = coords[1],
            longitude = coords[0],
            altitude = coords.getOrNull(2),
            updatedAt = PsaccTime.parseInstant(lastPosition.properties?.updatedAt),
        )
    } else {
        null
    }

    val precond = preconditioning?.airConditioning?.let {
        Preconditioning(
            // PSACC's own web UI treats anything but "Disabled" as on; also exclude the terminal states.
            active = it.status != null && INACTIVE_PRECONDITIONING.none { s -> s.equals(it.status, ignoreCase = true) },
            rawStatus = it.status,
            failureCause = it.failureCause,
            updatedAt = PsaccTime.parseInstant(it.updatedAt),
        )
    }

    return VehicleStatus(
        electric = electric,
        fuel = fuel,
        odometerKm = odometer?.mileage,
        outsideTempC = environment?.air?.temp,
        isDay = environment?.luminosity?.day,
        ignition = ignition?.type,
        moving = kinetic?.moving,
        speed = kinetic?.speed,
        auxBatteryVoltage = battery?.voltage,
        position = position,
        preconditioning = precond,
        doorLock = doorsState?.lockedState?.let(::lockStateOf),
        openDoors = doorsState?.opening.orEmpty()
            .filter { it.state.equals("Open", ignoreCase = true) }
            .mapNotNull { it.identifier },
        privacy = privacy?.state,
        serviceType = service?.type,
        updatedAt = listOfNotNull(
            electric?.updatedAt,
            fuel?.updatedAt,
            PsaccTime.parseInstant(odometer?.updatedAt),
        ).maxOrNull(),
    )
}

/** PSA lists every lock flag that applies; collapse them into a single state for the UI. */
internal fun lockStateOf(flags: List<String>): DoorLockState? {
    if (flags.isEmpty()) return null
    val unlocked = flags.any { it.contains("Unlocked", ignoreCase = true) }
    val locked = flags.any { it.equals("Locked", ignoreCase = true) || it.equals("SuperLocked", ignoreCase = true) }
    return when {
        flags.any { it.equals("Unlocked", ignoreCase = true) } -> DoorLockState.Unlocked
        locked && !unlocked -> DoorLockState.Locked
        locked || unlocked -> DoorLockState.Partial
        else -> null
    }
}

internal fun TripDto.toDomain(index: Int): Trip {
    val route = positions?.let { p ->
        p.lat.zip(p.long).mapNotNull { (lat, lng) -> if (lat != null && lng != null) LatLng(lat, lng) else null }
    }.orEmpty()
    return Trip(
        id = id ?: index,
        startAt = PsaccTime.parseInstant(startAt),
        duration = duration?.let { Duration.ofSeconds((it * 60).toLong()) },
        distance = distance,
        odometer = mileage,
        averageSpeed = speedAverage,
        energyKwh = consumption,
        kwhPer100 = consumptionKm,
        litresPer100 = consumptionFuelKm,
        temperatureC = temperature,
        altitudeDiff = altitudeDiff,
        route = route,
    )
}

internal fun ChargingSessionDto.toDomain() = ChargingSession(
    startAt = PsaccTime.parseInstant(startAt),
    stopAt = PsaccTime.parseInstant(stopAt),
    startLevel = startLevel,
    endLevel = endLevel,
    kwh = kw,
    price = price,
    co2 = co2,
    mode = chargingMode,
    odometer = mileage,
)

internal fun ServerSettingsDto.toDomain() = ServerSettings(
    currency = general?.currency?.takeIf { it.isNotBlank() } ?: ServerSettings.Default.currency,
    lengthUnit = general?.lengthUnit?.takeIf { it.isNotBlank() } ?: ServerSettings.Default.lengthUnit,
    dayPrice = electricity?.dayPrice,
    nightPrice = electricity?.nightPrice,
    nightStart = electricity?.nightHourStart,
    nightEnd = electricity?.nightHourEnd,
    chargerEfficiency = electricity?.chargerEfficiency,
)
