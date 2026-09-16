package pt.aguiarvieira.psacc.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Vehicle
import javax.inject.Inject

data class SettingsUiState(
    val config: ServerConfig? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val serverSettings: ServerSettings = ServerSettings.Default,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connection: ConnectionRepository,
    private val repository: VehicleRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        connection.config,
        repository.vehicles,
        repository.serverSettings,
    ) { config, vehicles, settings -> SettingsUiState(config, vehicles, settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(connection.config.value))

    init {
        viewModelScope.launch { repository.refreshServerSettings() }
    }

    /** Forgets the server; MainActivity observes the config and routes back to onboarding. */
    fun disconnect() {
        viewModelScope.launch {
            preferences.setSelectedVin(null)
            repository.clear()
            connection.disconnect()
        }
    }
}
