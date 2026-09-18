package pt.aguiarvieira.psacc.ui.feature.trips

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
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.userMessage
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TripsSummary(
    val count: Int,
    val totalDistance: Double,
    val totalEnergyKwh: Double,
    /** Distance-weighted mean kWh/100 over trips that report it. */
    val averageKwhPer100: Double?,
    val averageLitresPer100: Double?,
)

data class TripDay(val date: LocalDate?, val trips: List<Trip>)

data class TripsUiState(
    val trips: ContentState<List<TripDay>> = ContentState.Loading,
    val summary: TripsSummary? = null,
    val refreshing: Boolean = false,
    val settings: ServerSettings = ServerSettings.Default,
    /** True when the trips came from PSA itself, which serves them for every vehicle. */
    val perVehicle: Boolean = false,
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val content = MutableStateFlow(TripsUiState())
    private var vin: String? = null

    val state: StateFlow<TripsUiState> = combine(content, repository.serverSettings) { s, settings ->
        s.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    init {
        viewModelScope.launch {
            repository.selectedVehicle.filterNotNull().distinctUntilChangedBy { it.vin }.collect {
                vin = it.vin
                content.value = TripsUiState()
                load(initial = true)
            }
        }
    }

    fun refresh() = load(initial = false)

    fun retry() {
        content.update { it.copy(trips = ContentState.Loading) }
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        if (!initial) content.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            repository.trips(vin)
                .onSuccess { trips ->
                    content.update {
                        it.copy(
                            trips = ContentState.Data(groupByDay(trips)),
                            summary = summarize(trips),
                            refreshing = false,
                            perVehicle = trips.any { trip -> trip.startBatteryPercent != null },
                        )
                    }
                }
                .onFailure { e ->
                    content.update {
                        it.copy(
                            trips = if (it.trips is ContentState.Data) it.trips else ContentState.Error(e.userMessage()),
                            refreshing = false,
                        )
                    }
                }
        }
    }

    companion object {
        fun groupByDay(trips: List<Trip>, zone: ZoneId = ZoneId.systemDefault()): List<TripDay> =
            trips.groupBy { it.startAt?.atZone(zone)?.toLocalDate() }
                .map { (date, list) -> TripDay(date, list) }

        fun summarize(trips: List<Trip>): TripsSummary? {
            if (trips.isEmpty()) return null
            fun weighted(selector: (Trip) -> Double?): Double? {
                val usable = trips.filter { selector(it) != null && (it.distance ?: 0.0) > 0 }
                val distance = usable.sumOf { it.distance!! }
                if (distance <= 0) return null
                return usable.sumOf { selector(it)!! * it.distance!! } / distance
            }
            return TripsSummary(
                count = trips.size,
                totalDistance = trips.sumOf { it.distance ?: 0.0 },
                totalEnergyKwh = trips.sumOf { it.energyKwh ?: 0.0 },
                averageKwhPer100 = weighted { it.kwhPer100 },
                averageLitresPer100 = weighted { it.litresPer100?.takeIf { l -> l > 0 } },
            )
        }
    }
}
