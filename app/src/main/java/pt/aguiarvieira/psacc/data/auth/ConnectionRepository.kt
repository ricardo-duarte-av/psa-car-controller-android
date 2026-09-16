package pt.aguiarvieira.psacc.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import pt.aguiarvieira.psacc.data.network.PsaccClient
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.dto.VehicleDto
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectionRepository {
    /** The saved server; null until onboarding completes or after [disconnect]. */
    val config: StateFlow<ServerConfig?>

    /** Loads the persisted config into [config]. Call once at startup. */
    suspend fun restore()

    /**
     * Probes the server with the candidate settings (an authenticated `GET /get_vehicles`) and saves
     * them only if it answers like PSACC with at least one vehicle. Returns the vehicle count.
     */
    suspend fun verifyAndSave(url: String, username: String, password: String): Result<Int>

    fun disconnect()
}

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val store: ConfigStore,
    private val client: PsaccClient,
) : ConnectionRepository {

    private val _config = MutableStateFlow<ServerConfig?>(null)
    override val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    override suspend fun restore() {
        // First access creates the Keystore-backed master key — keep it off the main thread.
        _config.value = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrNull() }
    }

    override suspend fun verifyAndSave(url: String, username: String, password: String): Result<Int> {
        val baseUrl = ServerConfig.normalizeUrl(url)
            ?: return Result.failure(IllegalArgumentException("That doesn't look like a valid address."))
        val candidate = ServerConfig(baseUrl, username.trim(), password)
        return runCatching {
            val vehicles = client.get(candidate, ListSerializer(VehicleDto.serializer()), listOf("get_vehicles"))
            if (vehicles.isEmpty()) {
                throw PsaccException.Server(
                    "Connected, but PSA Car Controller has no vehicles yet. Finish its setup (PSA " +
                        "account login) in its web interface first.",
                )
            }
            withContext(Dispatchers.IO) { store.save(candidate) }
            _config.value = candidate
            vehicles.size
        }
    }

    override fun disconnect() {
        store.clear()
        _config.value = null
    }
}
