package pt.aguiarvieira.psacc.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import javax.inject.Inject

/** Decides where the app starts: the tabbed home when a server is saved, onboarding otherwise. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val connection: ConnectionRepository,
) : ViewModel() {

    val config: StateFlow<ServerConfig?> = connection.config

    private val _startState = MutableStateFlow<StartState>(StartState.Loading)
    val startState: StateFlow<StartState> = _startState.asStateFlow()

    /** Tab a notification asked to open; consumed by the home shell once shown. */
    private val _requestedTab = MutableStateFlow<String?>(null)
    val requestedTab: StateFlow<String?> = _requestedTab.asStateFlow()

    fun requestTab(tab: String) {
        _requestedTab.value = tab
    }

    fun consumeRequestedTab() {
        _requestedTab.value = null
    }

    init {
        viewModelScope.launch {
            connection.restore()
            _startState.value = if (connection.config.value != null) StartState.Home else StartState.Onboarding
        }
    }
}

sealed interface StartState {
    data object Loading : StartState
    data object Onboarding : StartState
    data object Home : StartState
}
