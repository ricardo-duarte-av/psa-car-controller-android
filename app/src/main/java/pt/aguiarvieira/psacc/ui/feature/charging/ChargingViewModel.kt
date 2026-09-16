package pt.aguiarvieira.psacc.ui.feature.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.userMessage
import javax.inject.Inject

data class ChargingSummary(val count: Int, val totalKwh: Double, val totalCost: Double?)

data class ChargingUiState(
    val sessions: ContentState<List<ChargingSession>> = ContentState.Loading,
    val summary: ChargingSummary? = null,
    val refreshing: Boolean = false,
    val settings: ServerSettings = ServerSettings.Default,
)

@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val content = MutableStateFlow(ChargingUiState())
    private var vin: String? = null

    val state: StateFlow<ChargingUiState> = combine(content, repository.serverSettings) { s, settings ->
        s.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargingUiState())

    init {
        viewModelScope.launch {
            repository.selectedVehicle.filterNotNull().distinctUntilChangedBy { it.vin }.collect {
                vin = it.vin
                content.value = ChargingUiState()
                load(initial = true)
            }
        }
    }

    fun refresh() = load(initial = false)

    fun retry() {
        content.update { it.copy(sessions = ContentState.Loading) }
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        val vin = vin ?: return
        if (!initial) content.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            repository.chargingSessions(vin)
                .onSuccess { list ->
                    content.update {
                        it.copy(sessions = ContentState.Data(list), summary = summarize(list), refreshing = false)
                    }
                }
                .onFailure { e ->
                    content.update {
                        it.copy(
                            sessions = if (it.sessions is ContentState.Data) it.sessions else ContentState.Error(e.userMessage()),
                            refreshing = false,
                        )
                    }
                }
        }
    }

    companion object {
        fun summarize(sessions: List<ChargingSession>): ChargingSummary? {
            if (sessions.isEmpty()) return null
            val priced = sessions.mapNotNull { it.price }
            return ChargingSummary(
                count = sessions.size,
                totalKwh = sessions.sumOf { it.kwh ?: 0.0 },
                totalCost = if (priced.isEmpty()) null else priced.sum(),
            )
        }
    }
}
