package pt.aguiarvieira.psacc.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pt.aguiarvieira.psacc.notifications.LastCheck
import pt.aguiarvieira.psacc.notifications.NotificationCategory
import pt.aguiarvieira.psacc.notifications.NotificationSettings
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "psacc_settings")

/** Non-secret, per-device UI preferences. */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** VIN of the vehicle the user last picked (multi-car accounts). */
    val selectedVin: Flow<String?> = context.dataStore.data.map { it[KEY_SELECTED_VIN] }

    suspend fun setSelectedVin(vin: String?) {
        context.dataStore.edit { prefs ->
            if (vin == null) prefs.remove(KEY_SELECTED_VIN) else prefs[KEY_SELECTED_VIN] = vin
        }
    }

    /**
     * Commands PSA refused for a given car ("vin|command"), so the app can grey them out instead of
     * making the user rediscover that e.g. remote door control isn't part of their subscription.
     */
    val refusedCommands: Flow<Set<String>> = context.dataStore.data
        .map { it[KEY_REFUSED_COMMANDS] ?: emptySet() }
        .distinctUntilChanged()

    suspend fun addRefusedCommand(vin: String, commandKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REFUSED_COMMANDS] = (prefs[KEY_REFUSED_COMMANDS] ?: emptySet()) + "$vin|$commandKey"
        }
    }

    suspend fun clearRefusedCommands() {
        context.dataStore.edit { it.remove(KEY_REFUSED_COMMANDS) }
    }

    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data.map { p ->
        val defaults = NotificationSettings()
        NotificationSettings(
            enabled = p[KEY_NOTIFY_ENABLED] ?: defaults.enabled,
            intervalMinutes = (p[KEY_NOTIFY_INTERVAL] ?: defaults.intervalMinutes)
                .coerceAtLeast(NotificationSettings.DEFAULT_INTERVAL_MINUTES),
            trips = p[categoryKey(NotificationCategory.TRIPS)] ?: defaults.trips,
            ignition = p[categoryKey(NotificationCategory.IGNITION)] ?: defaults.ignition,
            charging = p[categoryKey(NotificationCategory.CHARGING)] ?: defaults.charging,
            chargingSessions = p[categoryKey(NotificationCategory.CHARGING_SESSIONS)] ?: defaults.chargingSessions,
        )
    }.distinctUntilChanged()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFY_ENABLED] = enabled }
    }

    suspend fun setNotificationInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_NOTIFY_INTERVAL] = minutes }
    }

    suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        context.dataStore.edit { it[categoryKey(category)] = enabled }
    }

    val lastCheck: Flow<LastCheck?> = context.dataStore.data.map { p ->
        p[KEY_LAST_CHECK_AT]?.let { LastCheck(it, p[KEY_LAST_CHECK_ERROR]) }
    }.distinctUntilChanged()

    suspend fun recordCheck(atMillis: Long, error: String?) {
        context.dataStore.edit {
            it[KEY_LAST_CHECK_AT] = atMillis
            if (error == null) it.remove(KEY_LAST_CHECK_ERROR) else it[KEY_LAST_CHECK_ERROR] = error
        }
    }

    suspend fun clearLastCheck() {
        context.dataStore.edit {
            it.remove(KEY_LAST_CHECK_AT)
            it.remove(KEY_LAST_CHECK_ERROR)
        }
    }

    private companion object {
        val KEY_SELECTED_VIN = stringPreferencesKey("selected_vin")
        val KEY_REFUSED_COMMANDS = stringSetPreferencesKey("refused_commands")
        val KEY_NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val KEY_NOTIFY_INTERVAL = intPreferencesKey("notify_interval_minutes")
        val KEY_LAST_CHECK_AT = longPreferencesKey("last_check_at")
        val KEY_LAST_CHECK_ERROR = stringPreferencesKey("last_check_error")

        fun categoryKey(category: NotificationCategory) = booleanPreferencesKey("notify_${category.channelId}")
    }
}
