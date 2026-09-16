package pt.aguiarvieira.psacc.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.userMessage
import javax.inject.Inject

data class HomeUiState(
    val vehicles: ContentState<List<Vehicle>> = ContentState.Loading,
    val selected: Vehicle? = null,
)

/** Owns the vehicle list and selection that every tab keys off. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val loadState = MutableStateFlow<ContentState<Unit>>(ContentState.Loading)

    val state: StateFlow<HomeUiState> = combine(
        loadState,
        repository.vehicles,
        repository.selectedVehicle,
    ) { load, vehicles, selected ->
        HomeUiState(
            vehicles = when (load) {
                is ContentState.Data -> ContentState.Data(vehicles)
                is ContentState.Error -> if (vehicles.isNotEmpty()) ContentState.Data(vehicles) else ContentState.Error(load.message)
                ContentState.Loading -> if (vehicles.isNotEmpty()) ContentState.Data(vehicles) else ContentState.Loading
            },
            selected = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        load()
    }

    fun load() {
        loadState.value = ContentState.Loading
        viewModelScope.launch {
            // Units/currency are nice-to-have; a failure there must not block the app.
            launch { repository.refreshServerSettings() }
            repository.refreshVehicles()
                .onSuccess { loadState.value = ContentState.Data(Unit) }
                .onFailure { loadState.value = ContentState.Error(it.userMessage()) }
        }
    }

    fun select(vin: String) {
        viewModelScope.launch { repository.selectVehicle(vin) }
    }
}
