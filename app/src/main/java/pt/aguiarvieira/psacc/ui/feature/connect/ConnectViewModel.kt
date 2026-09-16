package pt.aguiarvieira.psacc.ui.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.ui.components.userMessage
import javax.inject.Inject

data class ConnectUiState(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
) {
    val canSubmit: Boolean get() = url.isNotBlank() && !connecting
    val isPlainHttp: Boolean get() = url.trim().startsWith("http://", ignoreCase = true)
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connection: ConnectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onLocalNetworkDenied() = _state.update {
        it.copy(error = "Local network access is needed to reach a server on your network.")
    }

    fun connect() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            connection.verifyAndSave(current.url, current.username, current.password)
                .onSuccess { _state.update { it.copy(connecting = false, connected = true) } }
                .onFailure { e -> _state.update { it.copy(connecting = false, error = e.userMessage()) } }
        }
    }
}
