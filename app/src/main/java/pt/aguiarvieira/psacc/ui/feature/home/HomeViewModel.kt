package pt.aguiarvieira.psacc.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.BuildConfig
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.domain.model.ServerCapabilities
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.userMessage
import pt.aguiarvieira.psacc.update.AppUpdateState
import pt.aguiarvieira.psacc.update.AppUpdates
import pt.aguiarvieira.psacc.update.UpdatePolicy
import pt.aguiarvieira.psacc.update.UpdatePrompt
import pt.aguiarvieira.psacc.util.VersionMatch
import javax.inject.Inject

data class HomeUiState(
    val vehicles: ContentState<List<Vehicle>> = ContentState.Loading,
    val selected: Vehicle? = null,
)

/** The notice above the tabs about the app's or the server's release. */
sealed interface UpdateBanner {
    /** An update downloaded in the background, installed by restarting. */
    data object Downloaded : UpdateBanner

    /** The server runs a newer release than the app; [canUpdate]: Play has it. */
    data class AppBehind(val serverVersion: String?, val canUpdate: Boolean) : UpdateBanner

    /** The server runs an older release ([serverVersion] null: one from before it said which). */
    data class ServerBehind(val serverVersion: String?) : UpdateBanner

    companion object {
        /** What the home shows, most pressing first. */
        fun of(update: AppUpdateState, match: VersionMatch, serverVersion: String?, dismissedNotice: String?): UpdateBanner? =
            when {
                update is AppUpdateState.Downloaded -> Downloaded
                match == VersionMatch.APP_BEHIND -> AppBehind(serverVersion, canUpdate = update is AppUpdateState.Available)
                match == VersionMatch.SERVER_BEHIND && dismissedNotice != noticeKey(serverVersion) -> ServerBehind(serverVersion)
                else -> null
            }

        /** Dismissing the server notice holds until either side changes release. */
        fun noticeKey(serverVersion: String?) = "${serverVersion ?: "old"}>${BuildConfig.VERSION_NAME}"
    }
}

/** Owns the vehicle list and selection that every tab keys off. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val appUpdates: AppUpdates,
    private val preferences: AppPreferences,
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

    val banner: StateFlow<UpdateBanner?> = combine(
        appUpdates.state,
        repository.capabilities,
        preferences.dismissedServerNotice,
    ) { update, capabilities, dismissed ->
        UpdateBanner.of(update, capabilities.versionMatch(BuildConfig.VERSION_NAME), capabilities.serverVersion, dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A flexible update offered by itself in this screen's life; Play's answer may come more than once. */
    private var promptedFlexible = false

    init {
        load()
        viewModelScope.launch {
            combine(appUpdates.state, repository.capabilities) { update, capabilities -> update to capabilities }
                .collect { (update, capabilities) -> maybePrompt(update, capabilities) }
        }
    }

    /** Play's update screen by itself, once the server's release is known (see [UpdatePolicy]). */
    private suspend fun maybePrompt(update: AppUpdateState, capabilities: ServerCapabilities) {
        if (!capabilities.probed || promptedFlexible) return
        val prompt = UpdatePolicy.prompt(
            state = update,
            match = capabilities.versionMatch(BuildConfig.VERSION_NAME),
            promptedVersionCode = preferences.updatePromptedVersionCode.first(),
            promptedImmediate = appUpdates.promptedImmediate,
        ) ?: return
        if (appUpdates.start(prompt) && prompt == UpdatePrompt.FLEXIBLE && update is AppUpdateState.Available) {
            promptedFlexible = true
            preferences.setUpdatePromptedVersionCode(update.versionCode)
        }
    }

    /** The banner's "Update": Play's full-screen update when the server is ahead, else the flexible one. */
    fun startUpdate() {
        val behind = repository.capabilities.value.versionMatch(BuildConfig.VERSION_NAME) == VersionMatch.APP_BEHIND
        if (!appUpdates.start(if (behind) UpdatePrompt.IMMEDIATE else UpdatePrompt.FLEXIBLE)) {
            appUpdates.start(UpdatePrompt.FLEXIBLE)
        }
    }

    fun completeUpdate() = appUpdates.completeUpdate()

    fun dismissServerNotice() {
        val serverVersion = repository.capabilities.value.serverVersion
        viewModelScope.launch { preferences.setDismissedServerNotice(UpdateBanner.noticeKey(serverVersion)) }
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
