package pt.aguiarvieira.psacc.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.SseFrame
import pt.aguiarvieira.psacc.data.network.dto.ChargingSessionDto
import pt.aguiarvieira.psacc.data.network.dto.MaintenanceDto
import pt.aguiarvieira.psacc.data.network.dto.PositionDto
import pt.aguiarvieira.psacc.data.network.dto.PsaTripDto
import pt.aguiarvieira.psacc.data.network.dto.PsaTripEnergyDto
import pt.aguiarvieira.psacc.data.network.dto.CommandResultDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleEventDto
import pt.aguiarvieira.psacc.domain.model.CommandOutcome
import pt.aguiarvieira.psacc.domain.model.CommandState
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
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
import pt.aguiarvieira.psacc.domain.model.Maintenance
import pt.aguiarvieira.psacc.domain.model.Preconditioning
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehiclePosition
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.util.PsaccTime
import java.time.Duration

private const val MS_TO_KMH = 3.6
private const val CL_PER_LITRE = 100.0

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
        startBatteryPercent = startLevel,
        endBatteryPercent = endLevel,
        startFuelPercent = startLevelFuel,
        // PSA copies its start level into the end one, so an identical end level isn't a reading.
        endFuelPercent = endLevelFuel?.takeIf { source != "psa" || it != startLevelFuel },
        // PSACC's own trips report 0 when they couldn't tell.
        fuelLitres = consumptionFuel?.takeIf { source != null },
        merged = source != null,
        inProgress = inProgress,
    )
}

/**
 * PSA's own trip. Duration is in seconds here (PSACC's own trips use minutes), consumption is per
 * energy type, and the route is the two endpoints when the car reported them — PSA gives no
 * intermediate points, so it's a straight line, not a traced route.
 */
internal fun PsaTripDto.toDomain(index: Int): Trip {
    fun level(list: List<PsaTripEnergyDto>?, type: String) =
        list?.firstOrNull { it.type.equals(type, ignoreCase = true) }?.level
    fun consumption(type: String) =
        energyConsumptions?.firstOrNull { it.type.equals(type, ignoreCase = true) }

    val fuel = energyConsumptions?.firstOrNull { it.type.equals("Fuel", ignoreCase = true) }
    val route = listOfNotNull(startPosition?.toLatLng(), stopPosition?.toLatLng())
    // PSA copies startEnergies into endEnergies (both ends always identical on the test car), so an
    // end level is only real when it differs.
    val startElectric = electricLevel(startEnergies)
    val endElectric = electricLevel(endEnergies)?.takeIf { it != startElectric }
    val startFuel = level(startEnergies, "Fuel")
    val endFuel = level(endEnergies, "Fuel")?.takeIf { it != startFuel }
    return Trip(
        id = id?.hashCode() ?: index,
        startAt = PsaccTime.parseInstant(startedAt),
        duration = duration?.let { Duration.ofSeconds(it.toLong()) },
        distance = distance,
        odometer = startMileage,
        // PSA reports speed in m/s: 16.53 for a trip of 20.5 km in 20.7 min, which is 59 km/h.
        averageSpeed = kinetic?.avgSpeed?.let { it * MS_TO_KMH },
        // Fuel is reported in centilitres, checked against the car's own trip computer: a 20.5 km
        // trip answered consumption 32.184 / avgConsumption 156.995, which the car displayed as
        // 0.3 L and 1.4 L/100 km. PSA reports no Electric entry on this car, so the electric
        // figures stay with PSACC's own trips, which derive them from the battery levels.
        energyKwh = null,
        kwhPer100 = null,
        litresPer100 = fuel?.avgConsumption?.let { it / CL_PER_LITRE },
        temperatureC = null,
        altitudeDiff = null,
        route = route,
        startBatteryPercent = startElectric,
        endBatteryPercent = endElectric,
        startFuelPercent = startFuel,
        endFuelPercent = endFuel,
        // maxSpeed is 0.0 on every trip of the test car, i.e. not reported.
        maxSpeed = kinetic?.maxSpeed?.takeIf { it > 0 }?.let { it * MS_TO_KMH },
        fuelLitres = fuel?.consumption?.let { it / CL_PER_LITRE },
        inProgress = done == false,
    )
}

/**
 * The battery level of a PSA trip end, or null when it can't be trusted. With the battery flat PSA
 * reports a range of 0 alongside a made-up level: 100% on three trips of 23/09/2026 driven just
 * before a charge that started at 5%, and 51% a few seconds after a trip that started at 1%. Real
 * low readings (1–9%) also come with a range of 0–2 km, so only a higher level is discarded.
 */
private fun electricLevel(energies: List<PsaTripEnergyDto>?): Double? {
    val electric = energies?.firstOrNull { it.type.equals("Electric", ignoreCase = true) } ?: return null
    val level = electric.level ?: return null
    return if (electric.autonomy == 0.0 && level > MAX_LEVEL_WITHOUT_RANGE) null else level
}

private const val MAX_LEVEL_WITHOUT_RANGE = 10.0

private fun PositionDto.toLatLng(): LatLng? {
    val coordinates = geometry?.coordinates
    return if (coordinates != null && coordinates.size >= 2) LatLng(coordinates[1], coordinates[0]) else null
}

internal fun MaintenanceDto.toDomain() = Maintenance(
    daysBefore = daysBefore?.toInt(),
    distanceBefore = mileageBefore,
    updatedAt = PsaccTime.parseInstant(updatedAt),
)

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

/**
 * A command endpoint's reply.
 *
 * The forked daemon answers with a [CommandResultDto]; the stock one answers `true` (or `null` for
 * the horn, which is fire-and-forget), and either may answer `{"error": "..."}` with HTTP 200 when a
 * rate limit is hit.
 */
internal fun parseCommandReply(reply: JsonElement, json: Json = DefaultJson): CommandOutcome = when {
    reply is JsonObject && reply["error"] != null ->
        throw PsaccException.Server((reply["error"] as? JsonPrimitive)?.contentOrNull ?: "The command failed.")

    reply is JsonObject && reply["status"] != null ->
        json.decodeFromJsonElement(CommandResultDto.serializer(), reply).toDomain()

    reply is JsonPrimitive && reply.booleanOrNull == false ->
        throw PsaccException.Server("The car rejected the command.")

    // `true`, `null`, or an object this app doesn't know: queued, nothing more will be known.
    else -> CommandOutcome(state = CommandState.Sent, message = SENT_MESSAGE)
}

internal fun CommandResultDto.toDomain(): CommandOutcome {
    val state = when (status?.lowercase()) {
        "success" -> CommandState.Success
        "failed" -> CommandState.Failed
        "pending" -> CommandState.Pending
        else -> CommandState.Sent
    }
    return CommandOutcome(
        state = state,
        message = message?.takeIf { it.isNotBlank() } ?: defaultMessage(state),
        correlationId = correlationId,
        action = action,
        returnCode = returnCode,
        reason = reason,
        updatedAt = PsaccTime.parseInstant(updatedAt),
    )
}

private fun defaultMessage(state: CommandState) = when (state) {
    CommandState.Success -> "The car accepted the command"
    CommandState.Failed -> "The car didn't accept the command"
    CommandState.Pending -> "Waiting for the car to answer"
    CommandState.Sent -> SENT_MESSAGE
}

private const val SENT_MESSAGE = "Sent to the car"

private val DefaultJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

/** Maps one `/events` frame to a domain event; unknown event types are ignored. */
internal fun SseFrame.toEvent(json: Json): PsaccEvent? = runCatching {
    when (event) {
        "vehicle" -> {
            val payload = data["data"] ?: return null
            val dto = json.decodeFromJsonElement(VehicleEventDto.serializer(), payload)
            PsaccEvent.VehicleUpdate(
                vin = dto.vin,
                at = PsaccTime.parseInstant(dto.date) ?: PsaccTime.parseInstant((data["date"] as? JsonPrimitive)?.contentOrNull),
                batteryLevel = dto.batteryLevel,
                autonomy = dto.autonomy,
                charging = dto.charging,
                chargingRate = dto.chargingRate,
                cablePlugged = dto.cablePlugged,
                preconditioning = dto.preconditioning,
            )
        }

        "psa_monitor" -> {
            val payload = data["data"] as? JsonObject
            PsaccEvent.MonitorUpdate(
                vin = (payload?.get("vin") as? JsonPrimitive)?.contentOrNull,
                label = (payload?.get("label") as? JsonPrimitive)?.contentOrNull,
                at = PsaccTime.parseInstant((data["date"] as? JsonPrimitive)?.contentOrNull),
            )
        }

        "command_result" -> {
            val payload = data["data"] ?: return null
            PsaccEvent.CommandUpdate(json.decodeFromJsonElement(CommandResultDto.serializer(), payload).toDomain())
        }

        else -> null
    }
}.getOrNull()
