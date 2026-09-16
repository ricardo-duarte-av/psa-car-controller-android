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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.ChargeControlSettings
import pt.aguiarvieira.psacc.domain.model.HourMinute
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
    /** Null when PSACC's charge control isn't configured for this car. */
    val chargeControl: ChargeControlSettings? = null,
    /** Commands awaiting PSACC's reply, so their buttons can show progress. */
    val pendingCommands: Set<CarCommand> = emptySet(),
    val savingChargeSettings: Boolean = false,
    val settings: ServerSettings = ServerSettings.Default,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off user feedback (command results), shown as snackbars. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var autoRefreshJob: Job? = null
    private var loadJob: Job? = null

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
    }

    private fun vin(): String? = _state.value.vehicle?.vin

    private fun loadAll(vin: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch { loadStatus(vin, fromCache = true, showErrors = false) }
            launch {
                repository.batterySoh(vin).onSuccess { soh -> _state.update { it.copy(batterySoh = soh) } }
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
            val result = repository.send(vin, command)
            _state.update { it.copy(pendingCommands = it.pendingCommands - command) }
            result
                .onSuccess {
                    _messages.send(successMessage(command))
                    // Commands travel PSACC → PSA → car over MQTT; give the car time to report back,
                    // then pick up whatever PSACC has cached by then.
                    delay(POST_COMMAND_REFRESH_MS)
                    loadStatus(vin, fromCache = true, showErrors = false)
                }
                .onFailure { _messages.send(it.userMessage()) }
        }
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
        CarCommand.WakeUp -> "Asked the car for fresh data — it can take a minute to arrive"
        is CarCommand.Preconditioning -> if (command.on) "Starting climate control" else "Stopping climate control"
        is CarCommand.Lock -> if (command.locked) "Locking the doors" else "Unlocking the doors"
        CarCommand.Horn -> "Honking"
        CarCommand.Lights -> "Flashing the lights"
        is CarCommand.Charge -> if (command.start) "Starting to charge" else "Stopping the charge"
    }

    override fun onCleared() {
        stopAutoRefresh()
    }

    private companion object {
        const val AUTO_REFRESH_MS = 60_000L
        const val POST_COMMAND_REFRESH_MS = 30_000L
    }
}
