package pt.aguiarvieira.psacc.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.network.EventSourceClient
import pt.aguiarvieira.psacc.data.network.PsaccClient
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.dto.ChargingSessionDto
import pt.aguiarvieira.psacc.data.network.dto.MaintenanceDto
import pt.aguiarvieira.psacc.data.network.dto.PicturesDto
import pt.aguiarvieira.psacc.data.network.dto.PsaTripDto
import pt.aguiarvieira.psacc.data.network.dto.ServerSettingsDto
import pt.aguiarvieira.psacc.data.network.dto.SohDto
import pt.aguiarvieira.psacc.data.network.dto.TripDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleStatusDto
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.ChargeControlSettings
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.CommandOutcome
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
import pt.aguiarvieira.psacc.domain.model.ServerCapabilities
import pt.aguiarvieira.psacc.domain.model.HourMinute
import pt.aguiarvieira.psacc.domain.model.Maintenance
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val connection: ConnectionRepository,
    private val client: PsaccClient,
    private val eventSourceClient: EventSourceClient,
    private val preferences: AppPreferences,
    private val json: Json,
) : VehicleRepository {

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    override val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    override val selectedVehicle: Flow<Vehicle?> =
        combine(_vehicles, preferences.selectedVin) { list, vin ->
            list.firstOrNull { it.vin == vin } ?: list.firstOrNull()
        }

    private val _serverSettings = MutableStateFlow(ServerSettings.Default)
    override val serverSettings: StateFlow<ServerSettings> = _serverSettings.asStateFlow()

    override suspend fun refreshVehicles() = call {
        client.get(config(), ListSerializer(VehicleDto.serializer()), listOf("get_vehicles"))
            .map { it.toDomain() }
            .also { _vehicles.value = it }
    }

    override fun clear() {
        _capabilities.value = ServerCapabilities.Basic
        _vehicles.value = emptyList()
        _serverSettings.value = ServerSettings.Default
    }

    override suspend fun selectVehicle(vin: String) = preferences.setSelectedVin(vin)

    override suspend fun refreshServerSettings() = call {
        client.get(config(), ServerSettingsDto.serializer(), listOf("settings"))
            .toDomain()
            .also { _serverSettings.value = it }
    }

    override suspend fun status(vin: String, fromCache: Boolean): Result<VehicleStatus> = call {
        client.get(
            config(),
            VehicleStatusDto.serializer(),
            listOf("get_vehicleinfo", vin),
            if (fromCache) mapOf("from_cache" to "1") else emptyMap(),
        ).toDomain()
    }

    override suspend fun batterySoh(vin: String): Result<Double?> = call {
        client.get(config(), SohDto.serializer(), listOf("battery", "soh", vin)).soh?.takeIf { it > 0 }
    }

    override suspend fun chargeControl(vin: String) = chargeControlCall(vin, emptyMap())

    override suspend fun setChargeThreshold(vin: String, percent: Int) =
        chargeControlCall(vin, mapOf("percentage" to percent.coerceIn(0, 100).toString()))

    override suspend fun setChargeStop(vin: String, stopAt: HourMinute?) =
        // PSACC treats a stop hour of 00:00 as "disabled".
        chargeControlCall(
            vin,
            mapOf("hour" to (stopAt?.hour ?: 0).toString(), "minute" to (stopAt?.minute ?: 0).toString()),
        )

    override suspend fun setScheduledChargeStart(vin: String, at: HourMinute) = call {
        val reply = client.getJson(
            config(),
            listOf("charge_hour"),
            mapOf("vin" to vin, "hour" to at.hour.toString(), "minute" to at.minute.toString()),
        )
        requireCommandSuccess(reply)
    }

    override suspend fun send(vin: String, command: CarCommand, waitSeconds: Int) = call {
        val path = when (command) {
            CarCommand.WakeUp -> listOf("wakeup", vin)
            is CarCommand.Preconditioning -> listOf("preconditioning", vin, command.on.bit())
            is CarCommand.Lock -> listOf("lock_door", vin, command.locked.bit())
            CarCommand.Horn -> listOf("horn", vin, HORN_COUNT.toString())
            CarCommand.Lights -> listOf("lights", vin, LIGHTS_SECONDS.toString())
            is CarCommand.Charge -> listOf("charge_now", vin, command.start.bit())
        }
        // Only ask the daemon to hold the request open when it knows how to answer.
        val query = if (waitSeconds > 0 && _capabilities.value.commandResults) {
            mapOf("wait" to waitSeconds.coerceAtMost(MAX_COMMAND_WAIT_SECONDS).toString())
        } else {
            emptyMap()
        }
        parseCommandReply(client.getJson(config(), path, query))
    }

    override suspend fun commandResult(correlationId: String): Result<CommandOutcome?> = call {
        try {
            parseCommandReply(client.getJson(config(), listOf("command", correlationId)))
        } catch (e: PsaccException.Http) {
            // 404: the daemon restarted, or it is the stock one with no such endpoint.
            if (e.code == 404) null else throw e
        }
    }

    private val _capabilities = MutableStateFlow(ServerCapabilities.Basic)
    override val capabilities: StateFlow<ServerCapabilities> = _capabilities.asStateFlow()

    override suspend fun refreshCapabilities(): ServerCapabilities {
        // /commands exists only on the fork that reports command results and streams events.
        val supported = runCatching { client.getJson(config(), listOf("commands")) }.isSuccess
        return ServerCapabilities(commandResults = supported, events = supported)
            .also { _capabilities.value = it }
    }

    override fun events(): Flow<PsaccEvent> = eventSourceClient
        .events(connection.config.value ?: throw PsaccException.NotConfigured())
        .mapNotNull { frame -> frame.toEvent(json) }

    override suspend fun trips(vin: String?): Result<List<Trip>> = call {
        // PSA's own trips are richer and exist per vehicle; older servers don't serve them (404).
        val psaTrips = vin?.let {
            runCatching {
                client.get(config(), ListSerializer(PsaTripDto.serializer()), listOf("vehicles", it, "psa_trips"))
            }.getOrNull()
        }
        if (psaTrips != null) {
            _psaTripsAvailable.value = true
            return@call psaTrips.mapIndexed { index, dto -> dto.toDomain(index) }.sortedByDescending { it.startAt }
        }
        _psaTripsAvailable.value = false
        // An empty history comes back as {} rather than [].
        when (val body = client.getJson(config(), listOf("vehicles", "trips"))) {
            is JsonArray -> body.mapIndexedNotNull { index, element ->
                runCatching { json.decodeFromJsonElement(TripDto.serializer(), element).toDomain(index) }.getOrNull()
            }
            else -> emptyList()
        }.sortedByDescending { it.startAt }
    }

    /** True once PSA's own trips have answered, so the UI can drop the "first vehicle only" note. */
    private val _psaTripsAvailable = MutableStateFlow(false)
    val psaTripsAvailable: StateFlow<Boolean> = _psaTripsAvailable.asStateFlow()

    override suspend fun maintenance(vin: String): Result<Maintenance?> = call {
        runCatching {
            client.get(config(), MaintenanceDto.serializer(), listOf("vehicles", vin, "maintenance")).toDomain()
        }.getOrNull()
    }

    override suspend fun pictures(vin: String): Result<List<String>> = call {
        val config = config()
        runCatching {
            client.get(config, PicturesDto.serializer(), listOf("vehicles", vin, "pictures"))
                .pictures
                // The daemon returns paths relative to its base url; make them absolute.
                .map { "${config.baseUrl.trimEnd('/')}/${it.trimStart('/')}" }
        }.getOrDefault(emptyList())
    }

    override suspend fun chargingSessions(vin: String): Result<List<ChargingSession>> = call {
        client.get(config(), ListSerializer(ChargingSessionDto.serializer()), listOf("vehicles", "chargings"))
            .filter { it.vin == null || it.vin == vin }
            .map { it.toDomain() }
            .sortedByDescending { it.startAt }
    }

    private suspend fun chargeControlCall(vin: String, params: Map<String, String>) = call {
        val reply = client.getJson(config(), listOf("charge_control"), mapOf("vin" to vin) + params)
        parseChargeControl(reply)
    }

    private fun config() = connection.config.value ?: throw PsaccException.NotConfigured()

    private companion object {
        const val HORN_COUNT = 1
        const val LIGHTS_SECONDS = 10

        /** The daemon caps `?wait=` at 30 s. */
        const val MAX_COMMAND_WAIT_SECONDS = 30
    }
}

private fun Boolean.bit() = if (this) "1" else "0"

/** runCatching that doesn't swallow coroutine cancellation. */
private inline fun <T> call(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * PSACC command endpoints answer `true` on success, `null` for the fire-and-forget ones (horn), and
 * `{"error": "..."}` — still HTTP 200 — when e.g. PSA's rate limit is hit.
 */
internal fun requireCommandSuccess(reply: JsonElement) {
    when (reply) {
        is JsonNull -> Unit
        is JsonPrimitive -> if (reply.booleanOrNull == false) throw PsaccException.Server("The car rejected the command.")
        is JsonObject -> serverError(reply)?.let { throw PsaccException.Server(it) }
        is JsonArray -> Unit
    }
}

private fun serverError(obj: JsonObject): String? = (obj["error"] as? JsonPrimitive)?.contentOrNull

/**
 * `/charge_control` returns the ChargeControl object's `__dict__` (`percentage_threshold`,
 * `_stop_hour: [h, m] | null`, ...) or `{"error": ...}` when charge control isn't configured.
 */
internal fun parseChargeControl(reply: JsonElement): ChargeControlSettings? {
    val obj = reply as? JsonObject ?: throw PsaccException.BadResponse(null)
    if (serverError(obj) != null) return null
    val threshold = (obj["percentage_threshold"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 100
    val stop = (obj["_stop_hour"] as? JsonArray)?.let { arr ->
        val h = (arr.getOrNull(0) as? JsonPrimitive)?.intOrNull
        val m = (arr.getOrNull(1) as? JsonPrimitive)?.intOrNull
        if (h != null && m != null && !(h == 0 && m == 0)) HourMinute(h, m) else null
    }
    return ChargeControlSettings(thresholdPercent = threshold, stopAt = stop)
}
