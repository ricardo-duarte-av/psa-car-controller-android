package pt.aguiarvieira.psacc.update

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.aguiarvieira.psacc.util.VersionMatch
import javax.inject.Inject
import javax.inject.Singleton

/** What Google Play says about a newer release of the app. */
sealed interface AppUpdateState {
    /** None, or Play can't tell: the app wasn't installed from Play (debug build, GitHub APK). */
    data object None : AppUpdateState
    data class Available(val versionCode: Int, val immediateAllowed: Boolean, val flexibleAllowed: Boolean) :
        AppUpdateState
    data object Downloading : AppUpdateState
    /** A flexible update is downloaded: it installs when the app restarts ([AppUpdates.completeUpdate]). */
    data object Downloaded : AppUpdateState
}

/** Play's own update screens: full screen until updated, or a sheet with a download in the background. */
enum class UpdatePrompt { IMMEDIATE, FLEXIBLE }

object UpdatePolicy {
    /**
     * Whether to show Play's update screen without being asked. The app older than its server (they
     * release in step) gets the full-screen one, once per launch; any other update the flexible one,
     * once per version; after that the home banner offers it.
     */
    fun prompt(
        state: AppUpdateState,
        match: VersionMatch,
        promptedVersionCode: Int?,
        promptedImmediate: Boolean,
    ): UpdatePrompt? {
        if (state !is AppUpdateState.Available) return null
        if (match == VersionMatch.APP_BEHIND && state.immediateAllowed) {
            return if (promptedImmediate) null else UpdatePrompt.IMMEDIATE
        }
        if (!state.flexibleAllowed || promptedVersionCode == state.versionCode) return null
        return UpdatePrompt.FLEXIBLE
    }
}

/**
 * Google Play in-app updates. [MainActivity][pt.aguiarvieira.psacc.MainActivity] attaches its result
 * launcher and refreshes on every resume; the home screen decides when to prompt ([UpdatePolicy]).
 */
@Singleton
class AppUpdates @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Play's latest answer; one can start a single update flow. */
    private var info: AppUpdateInfo? = null
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    /** The full-screen update was shown in this run of the app. */
    var promptedImmediate = false
        private set

    private val listener = InstallStateUpdatedListener { install ->
        when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> _state.value = AppUpdateState.Downloading
            InstallStatus.DOWNLOADED -> _state.value = AppUpdateState.Downloaded
            InstallStatus.CANCELED, InstallStatus.FAILED -> refresh()
            else -> Unit
        }
    }

    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        this.launcher = launcher
        manager.registerListener(listener)
    }

    fun detach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (this.launcher !== launcher) return
        this.launcher = null
        manager.unregisterListener(listener)
    }

    /** Asks Play again; on every resume, as an update may have been published or finished meanwhile. */
    fun refresh() {
        manager.appUpdateInfo
            .addOnSuccessListener { update ->
                info = update
                val availability = update.updateAvailability()
                val status = update.installStatus()
                _state.value = when {
                    status == InstallStatus.DOWNLOADED -> AppUpdateState.Downloaded
                    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        // A full-screen update left before it finished: Play asks to go back to it.
                        start(UpdatePrompt.IMMEDIATE)
                        AppUpdateState.Downloading
                    }
                    status == InstallStatus.PENDING || status == InstallStatus.DOWNLOADING -> AppUpdateState.Downloading
                    availability == UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateState.Available(
                        versionCode = update.availableVersionCode(),
                        immediateAllowed = update.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                        flexibleAllowed = update.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                    )
                    else -> AppUpdateState.None
                }
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "Play has no update information (not installed from Play?)", e)
                _state.value = AppUpdateState.None
            }
    }

    /** Shows Play's update screen; false when there is nothing to update or no screen to show it on. */
    fun start(prompt: UpdatePrompt): Boolean {
        val update = info ?: return false
        val activityLauncher = launcher ?: return false
        val type = if (prompt == UpdatePrompt.IMMEDIATE) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        if (!update.isUpdateTypeAllowed(type)) return false
        if (prompt == UpdatePrompt.IMMEDIATE) promptedImmediate = true
        info = null
        return manager.startUpdateFlowForResult(update, activityLauncher, AppUpdateOptions.newBuilder(type).build())
    }

    /** Installs a downloaded flexible update; the app restarts. */
    fun completeUpdate() {
        manager.completeUpdate()
    }

    private companion object {
        const val TAG = "AppUpdates"
    }
}
