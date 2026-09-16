package pt.aguiarvieira.psacc.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository
import pt.aguiarvieira.psacc.data.settings.AppPreferences
import pt.aguiarvieira.psacc.di.ApplicationScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the periodic [VehicleCheckWorker] in line with the user's settings: scheduled while
 * notifications are on and a server is configured, cancelled otherwise.
 *
 * WorkManager periodic work survives reboots, app updates and process death, needs no foreground
 * service, and is the platform-sanctioned way to poll. The trade-off is timing: the period can't be
 * shorter than 15 minutes, and Doze / battery optimisation may defer runs further.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val connection: ConnectionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            // Load the saved server first: in a process started just to run the worker nothing else
            // has, and treating "not loaded yet" as "disconnected" would cancel the very work running.
            if (connection.config.value == null) connection.restore()
            combine(preferences.notificationSettings, connection.config) { settings, config ->
                if (settings.enabled && config != null) settings.intervalMinutes else null
            }
                .distinctUntilChanged()
                .collect { interval -> if (interval != null) schedule(interval) else cancel() }
        }
    }

    /** Runs a check right away, regardless of the periodic schedule (Settings → Check now). */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<VehicleCheckWorker>()
            .setConstraints(networkConstraint())
            .setInputData(workDataOf(VehicleCheckWorker.KEY_MANUAL to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CHECK_NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedule(intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<VehicleCheckWorker>(
            intervalMinutes.toLong().coerceAtLeast(MIN_PERIODIC_MINUTES),
            TimeUnit.MINUTES,
        ).setConstraints(networkConstraint()).build()
        // UPDATE re-specs existing work (e.g. a new interval) without resetting its schedule.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    private fun networkConstraint() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val PERIODIC_WORK = "psacc-vehicle-check"
        const val CHECK_NOW_WORK = "psacc-vehicle-check-now"
        const val MIN_PERIODIC_MINUTES = 15L
    }
}
