package pt.aguiarvieira.psacc.notifications

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.repository.VehicleRepository
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.ui.components.userMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One background poll: reads PSACC's *cached* status (no PSA API quota used), trips and charging
 * sessions for every vehicle, diffs them against the persisted [VehicleWatch]es and posts
 * notifications for what changed.
 */
@Singleton
class BackgroundChecker @Inject constructor(
    private val connection: ConnectionRepository,
    private val repository: VehicleRepository,
    private val preferences: AppPreferences,
    private val watchStore: WatchStore,
    private val notifier: VehicleNotifier,
) {
    private val mutex = Mutex()

    suspend fun runOnce() = mutex.withLock {
        // A WorkManager run is often a fresh process in which nothing has loaded the config yet.
        if (connection.config.value == null) connection.restore()
        if (connection.config.value == null) {
            Log.d(TAG, "no server configured; skipping")
            return@withLock
        }

        val vehicles = repository.refreshVehicles().getOrElse { e ->
            Log.w(TAG, "vehicle list failed", e)
            preferences.recordCheck(System.currentTimeMillis(), e.userMessage())
            return@withLock
        }
        val settings = preferences.notificationSettings.first()
        val serverSettings = repository.refreshServerSettings().getOrElse { repository.serverSettings.value }
        val previous = watchStore.load()
        val updated = previous.toMutableMap()
        val errors = mutableListOf<String>()

        vehicles.forEachIndexed { index, vehicle ->
            val status = repository.status(vehicle.vin, fromCache = true)
                .onFailure { errors += it.userMessage() }.getOrNull()
            val sessions = repository.chargingSessions(vehicle.vin)
                .onFailure { errors += it.userMessage() }.getOrNull()
            // PSA serves trips per vehicle; PSACC's own fallback only covers its first one.
            val trips = if (index == 0 || repository.capabilities.value.commandResults) {
                repository.trips(vehicle.vin).onFailure { errors += it.userMessage() }.getOrNull()
            } else {
                null
            }

            val detection = VehicleEventDetector.detect(
                previous[vehicle.vin],
                VehicleSnapshot(vehicle, status, trips, sessions),
            )
            // The watch always advances, even for muted categories, so unmuting never replays history.
            updated[vehicle.vin] = detection.watch
            val wanted = detection.events.filter { settings.allows(it.category) }
            Log.d(TAG, "${vehicle.vin}: ${detection.events.size} events, posting ${wanted.size}")
            notifier.post(vehicle, wanted, serverSettings)
        }

        watchStore.save(updated)
        preferences.recordCheck(System.currentTimeMillis(), errors.distinct().firstOrNull())
    }

    private companion object {
        const val TAG = "BackgroundChecker"
    }
}
