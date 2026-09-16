package pt.aguiarvieira.psacc.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import pt.aguiarvieira.psacc.data.settings.AppPreferences

/**
 * WorkManager entry point for [BackgroundChecker]. PSACC pushes nothing, so this pull loop is the only
 * way to learn about changes while the app is closed.
 *
 * Dependencies come through a Hilt [EntryPoint] rather than `@HiltWorker`, keeping the androidx.hilt
 * worker KSP processor (and a custom WorkManager initializer) out of this bleeding-edge build — the
 * same choice slskdAndroid made.
 */
class VehicleCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun backgroundChecker(): BackgroundChecker
        fun appPreferences(): AppPreferences
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val manual = inputData.getBoolean(KEY_MANUAL, false)

        // The scheduler cancels periodic work when notifications are switched off, but a run already
        // queued at that moment would still fire.
        if (!manual && !deps.appPreferences().notificationSettings.first().enabled) {
            return Result.success()
        }
        // A failed check isn't worth an exponential-backoff retry: the next period comes soon enough.
        runCatching { deps.backgroundChecker().runOnce() }
            .onFailure { Log.w(TAG, "check failed", it) }
        return Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual"
        private const val TAG = "VehicleCheckWorker"
    }
}
