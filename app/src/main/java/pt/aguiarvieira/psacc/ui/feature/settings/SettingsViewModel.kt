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
import pt.aguiarvieira.psacc.notifications.LastCheck
import pt.aguiarvieira.psacc.notifications.NotificationCategory
import pt.aguiarvieira.psacc.notifications.NotificationScheduler
import pt.aguiarvieira.psacc.notifications.NotificationSettings
import pt.aguiarvieira.psacc.notifications.VehicleNotifier
import pt.aguiarvieira.psacc.notifications.WatchStore
import javax.inject.Inject

data class SettingsUiState(
    val config: ServerConfig? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val serverSettings: ServerSettings = ServerSettings.Default,
    val notifications: NotificationSettings = NotificationSettings(),
    val lastCheck: LastCheck? = null,
    val fuelPricePerLitre: Double = 0.0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connection: ConnectionRepository,
    private val repository: VehicleRepository,
    private val preferences: AppPreferences,
    private val scheduler: NotificationScheduler,
    private val watchStore: WatchStore,
    private val notifier: VehicleNotifier,
) : ViewModel() {

    // combine caps at 5 flows; nest the 6th (fuel price) on top of the first five.
    private val base = combine(
        connection.config,
        repository.vehicles,
        repository.serverSettings,
        preferences.notificationSettings,
        preferences.lastCheck,
    ) { config, vehicles, settings, notifications, lastCheck ->
        SettingsUiState(config, vehicles, settings, notifications, lastCheck)
    }

    val state: StateFlow<SettingsUiState> = combine(base, preferences.fuelPricePerLitre) { s, fuelPrice ->
        s.copy(fuelPricePerLitre = fuelPrice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(connection.config.value))

    /** Whether the system currently lets the app post (permission granted and not blocked). */
    fun canPostNotifications(): Boolean = notifier.canPost()

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
            // Establish the baseline straight away, so the first periodic run can already notify.
            if (enabled) scheduler.checkNow()
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch { preferences.setNotificationInterval(minutes) }
    }

    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch { preferences.setCategoryEnabled(category, enabled) }
    }

    fun checkNow() = scheduler.checkNow()

    fun setFuelPrice(price: Double) {
        viewModelScope.launch { preferences.setFuelPricePerLitre(price) }
    }

    /** Forgets which controls PSA refused, so they can be tried again (e.g. after a subscription change). */
    fun resetUnavailableControls() {
        viewModelScope.launch { preferences.clearRefusedCommands() }
    }

    init {
        viewModelScope.launch { repository.refreshServerSettings() }
    }

    /** Forgets the server; MainActivity observes the config and routes back to onboarding. */
    fun disconnect() {
        viewModelScope.launch {
            preferences.setSelectedVin(null)
            // A different server may have different cars/history: start the watch from scratch.
            watchStore.clear()
            preferences.clearLastCheck()
            repository.clear()
            connection.disconnect()
        }
    }
}
