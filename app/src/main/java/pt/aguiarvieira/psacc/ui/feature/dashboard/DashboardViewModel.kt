package pt.aguiarvieira.psacc.ui.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.ChargeControlSettings
import pt.aguiarvieira.psacc.domain.model.CommandOutcome
import pt.aguiarvieira.psacc.domain.model.CommandState
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
import pt.aguiarvieira.psacc.domain.model.ServerCapabilities
import pt.aguiarvieira.psacc.domain.model.key
import pt.aguiarvieira.psacc.domain.model.matchesAction
import pt.aguiarvieira.psacc.domain.model.HourMinute
import pt.aguiarvieira.psacc.domain.model.Maintenance
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.userMessage
import javax.inject.Inject

data class DashboardUiState(
    val vehicle: Vehicle? = null,
    val status: ContentState<VehicleStatus> = ContentState.Loading,
    /** Pull-to-refresh in flight (a live PSA query, not the cache). */
    val refreshing: Boolean = false,
    val batterySoh: Double? = null,
    /** Next service, when the server serves it. */
    val maintenance: Maintenance? = null,
    /** Null when PSACC's charge control isn't configured for this car. */
    val chargeControl: ChargeControlSettings? = null,
    /** Commands awaiting PSACC's reply, so their buttons can show progress. */
    val pendingCommands: Set<CarCommand> = emptySet(),
    /** Commands PSA refuses for this car; their controls are shown as unavailable. */
    val refusedCommands: Set<CarCommand> = emptySet(),
    /** What this server can do (report command results, stream events). */
    val capabilities: ServerCapabilities = ServerCapabilities.Basic,
    /** Latest pushed values, fresher than the polled status; null when nothing has arrived. */
    val live: PsaccEvent.VehicleUpdate? = null,
    val savingChargeSettings: Boolean = false,
    val settings: ServerSettings = ServerSettings.Default,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    /** Correlation ids whose result is still awaited, from the stream or by polling. */
    private val awaitedCommands = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off user feedback (command results), shown as snackbars. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var autoRefreshJob: Job? = null
    private var loadJob: Job? = null
    private var eventJob: Job? = null
    private var lastEventRefresh = 0L

    init {
        viewModelScope.launch {
            repository.selectedVehicle
                .filterNotNull()
                .distinctUntilChangedBy { it.vin }
                .collect { vehicle ->
                    _state.value = DashboardUiState(vehicle = vehicle, settings = repository.serverSettings.value)
                    loadAll(vehicle.vin)
                }
        }
        viewModelScope.launch {
            repository.serverSettings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            repository.capabilities.collect { c -> _state.update { it.copy(capabilities = c) } }
        }
        viewModelScope.launch {
            combine(repository.selectedVehicle, preferences.refusedCommands) { vehicle, refused ->
                vehicle?.vin?.let { vin -> ALL_COMMANDS.filter { "$vin|${it.key()}" in refused }.toSet() }.orEmpty()
            }.collect { refused -> _state.update { it.copy(refusedCommands = refused) } }
        }
    }

    private fun vin(): String? = _state.value.vehicle?.vin

    private fun loadAll(vin: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch { repository.refreshCapabilities() }
            launch { loadStatus(vin, fromCache = true, showErrors = false) }
            launch {
                repository.batterySoh(vin).onSuccess { soh -> _state.update { it.copy(batterySoh = soh) } }
            }
            launch {
                repository.maintenance(vin).onSuccess { m -> _state.update { it.copy(maintenance = m) } }
            }
            launch {
                repository.chargeControl(vin).onSuccess { cc -> _state.update { it.copy(chargeControl = cc) } }
            }
        }
    }

    fun retry() {
        val vin = vin() ?: return
        _state.update { it.copy(status = ContentState.Loading) }
        loadAll(vin)
    }

    /** Pull-to-refresh: ask PSACC to query PSA now instead of serving its cache. */
    fun refresh() {
        val vin = vin() ?: return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            loadStatus(vin, fromCache = false, showErrors = true)
            _state.update { it.copy(refreshing = false) }
        }
    }

    /** Re-reads PSACC's cache periodically while the dashboard is on screen. */
    fun startAutoRefresh() {
        startEventStream()
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_MS)
                vin()?.let { loadStatus(it, fromCache = true, showErrors = false) }
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        eventJob?.cancel()
        eventJob = null
    }

    /**
     * Follows the daemon's event stream while the dashboard is on screen: pushed values land in
     * [DashboardUiState.live] and nudge a cached re-read, so the rest of the status catches up
     * without waiting for the next poll. Only on servers that stream; reconnects with a backoff.
     */
    private fun startEventStream() {
        if (eventJob?.isActive == true) return
        eventJob = viewModelScope.launch {
            repository.capabilities.collectLatest { capabilities ->
                if (!capabilities.events) return@collectLatest
                var backoff = EVENT_RETRY_MIN_MS
                while (isActive) {
                    try {
                        repository.events().collect { event ->
                            backoff = EVENT_RETRY_MIN_MS
                            onEvent(event)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Dropped stream (server restart, network change): back off and re-subscribe.
                    }
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(EVENT_RETRY_MAX_MS)
                }
            }
        }
    }

    private suspend fun onEvent(event: PsaccEvent) {
        when (event) {
            is PsaccEvent.VehicleUpdate -> {
                val vin = vin()
                if (event.vin != null && vin != null && event.vin != vin) return
                _state.update { it.copy(live = event) }
                // The event carries only part of the picture; re-read the cache to catch up, but
                // not on every event — a charging car emits them continuously.
                val now = System.currentTimeMillis()
                if (now - lastEventRefresh > EVENT_REFRESH_THROTTLE_MS) {
                    lastEventRefresh = now
                    vin?.let { loadStatus(it, fromCache = true, showErrors = false) }
                }
            }

            is PsaccEvent.MonitorUpdate -> {
                val vin = vin()
                if (event.vin != null && vin != null && event.vin != vin) return
                // PSA only pushes when something actually changed, so this is worth a read straight
                // away, unlike the periodic vehicle events.
                lastEventRefresh = System.currentTimeMillis()
                vin?.let { loadStatus(it, fromCache = true, showErrors = false) }
            }

            is PsaccEvent.CommandUpdate -> {
                val outcome = event.outcome
                if (outcome.settled && outcome.correlationId != null && outcome.correlationId in awaitedCommands) {
                    awaitedCommands -= outcome.correlationId
                    report(outcome)
                }
            }
        }
    }

    private suspend fun loadStatus(vin: String, fromCache: Boolean, showErrors: Boolean) {
        repository.status(vin, fromCache)
            .onSuccess { status ->
                if (vin() == vin) _state.update { it.copy(status = ContentState.Data(status)) }
            }
            .onFailure { e ->
                if (vin() != vin) return
                val hasData = _state.value.status is ContentState.Data
                if (!hasData) {
                    _state.update { it.copy(status = ContentState.Error(e.userMessage())) }
                } else if (showErrors) {
                    _messages.send(e.userMessage())
                }
            }
    }

    fun send(command: CarCommand) {
        val vin = vin() ?: return
        if (command in _state.value.pendingCommands) return
        _state.update { it.copy(pendingCommands = it.pendingCommands + command) }
        viewModelScope.launch {
            // On a daemon that reports results, hold the request open for the car's answer; the
            // stock one ignores this and answers straight away.
            val result = repository.send(vin, command, waitSeconds = COMMAND_WAIT_SECONDS)
            _state.update { it.copy(pendingCommands = it.pendingCommands - command) }
            result
                .onSuccess { outcome ->
                    handleOutcome(vin, command, outcome)
                    // Commands travel PSACC → PSA → car over MQTT; give the car time to report back,
                    // then pick up whatever PSACC has cached by then.
                    delay(POST_COMMAND_REFRESH_MS)
                    loadStatus(vin, fromCache = true, showErrors = false)
                }
                .onFailure { _messages.send(it.userMessage()) }
        }
    }

    private suspend fun handleOutcome(vin: String, command: CarCommand, outcome: CommandOutcome) {
        when (outcome.state) {
            CommandState.Sent -> _messages.send("${actionName(command)} — sent to the car")
            CommandState.Success -> _messages.send(successMessage(command))
            CommandState.Failed -> {
                _messages.send("${actionName(command)}: ${outcome.message}")
                // PSA refuses this service for this car: remember it so the control can say so.
                if (outcome.refused) preferences.addRefusedCommand(vin, command.key())
            }
            CommandState.Pending -> {
                _messages.send("${actionName(command)} — waiting for the car")
                outcome.correlationId?.let { awaitPending(it) }
            }
        }
    }

    /**
     * Follows a command the car hasn't answered yet. The event stream usually delivers the result
     * first (see [onEvent]); this polls as well, for servers whose stream isn't reachable.
     */
    private fun awaitPending(correlationId: String) {
        awaitedCommands += correlationId
        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + COMMAND_FOLLOW_UP_MS
            while (isActive && correlationId in awaitedCommands && System.currentTimeMillis() < deadline) {
                delay(COMMAND_POLL_INTERVAL_MS)
                if (correlationId !in awaitedCommands) return@launch
                val outcome = repository.commandResult(correlationId).getOrNull() ?: continue
                if (outcome.settled) {
                    awaitedCommands -= correlationId
                    report(outcome)
                    vin()?.let { loadStatus(it, fromCache = true, showErrors = false) }
                    return@launch
                }
            }
            awaitedCommands -= correlationId
        }
    }

    private suspend fun report(outcome: CommandOutcome) {
        _messages.send(outcome.message)
        val vin = vin()
        val command = outcome.action?.let { action -> ALL_COMMANDS.firstOrNull { it.matchesAction(action) } }
        if (outcome.refused && vin != null && command != null) preferences.addRefusedCommand(vin, command.key())
    }

    fun setChargeThreshold(percent: Int) = updateChargeControl { repository.setChargeThreshold(it, percent) }

    fun setChargeStop(at: HourMinute?) = updateChargeControl { repository.setChargeStop(it, at) }

    private fun updateChargeControl(block: suspend (String) -> Result<ChargeControlSettings?>) {
        val vin = vin() ?: return
        _state.update { it.copy(savingChargeSettings = true) }
        viewModelScope.launch {
            block(vin)
                .onSuccess { cc ->
                    _state.update { it.copy(chargeControl = cc, savingChargeSettings = false) }
                    _messages.send("Charge limit saved")
                }
                .onFailure { e ->
                    _state.update { it.copy(savingChargeSettings = false) }
                    _messages.send(e.userMessage())
                }
        }
    }

    fun setScheduledChargeStart(at: HourMinute) {
        val vin = vin() ?: return
        _state.update { it.copy(savingChargeSettings = true) }
        viewModelScope.launch {
            val result = repository.setScheduledChargeStart(vin, at)
            _state.update { it.copy(savingChargeSettings = false) }
            result
                .onSuccess { _messages.send("Charging will start at $at") }
                .onFailure { _messages.send(it.userMessage()) }
        }
    }

    private fun successMessage(command: CarCommand): String = when (command) {
        CarCommand.WakeUp -> "The car is sending fresh data"
        is CarCommand.Preconditioning -> if (command.on) "Climate control started" else "Climate control stopped"
        is CarCommand.Lock -> if (command.locked) "Doors locked" else "Doors unlocked"
        CarCommand.Horn -> "The car honked"
        CarCommand.Lights -> "The lights flashed"
        is CarCommand.Charge -> if (command.start) "Charging started" else "Charging stopped"
    }

    private fun actionName(command: CarCommand): String = when (command) {
        CarCommand.WakeUp -> "Update"
        is CarCommand.Preconditioning -> "Climate"
        is CarCommand.Lock -> if (command.locked) "Lock" else "Unlock"
        CarCommand.Horn -> "Horn"
        CarCommand.Lights -> "Lights"
        is CarCommand.Charge -> if (command.start) "Charge" else "Stop charging"
    }

    override fun onCleared() {
        stopAutoRefresh()
    }

    private companion object {
        const val AUTO_REFRESH_MS = 60_000L
        const val POST_COMMAND_REFRESH_MS = 30_000L

        /** The car usually answers within ~30 s; hold the request for part of that, then follow up. */
        const val COMMAND_WAIT_SECONDS = 10
        const val COMMAND_FOLLOW_UP_MS = 120_000L
        const val COMMAND_POLL_INTERVAL_MS = 3_000L

        const val EVENT_RETRY_MIN_MS = 2_000L
        const val EVENT_RETRY_MAX_MS = 60_000L
        const val EVENT_REFRESH_THROTTLE_MS = 30_000L

        val ALL_COMMANDS = listOf(
            CarCommand.WakeUp,
            CarCommand.Preconditioning(true),
            CarCommand.Lock(true),
            CarCommand.Horn,
            CarCommand.Lights,
            CarCommand.Charge(true),
        )
    }
}
