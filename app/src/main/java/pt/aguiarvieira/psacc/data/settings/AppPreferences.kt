package pt.aguiarvieira.psacc.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    private companion object {
        val KEY_SELECTED_VIN = stringPreferencesKey("selected_vin")
    }
}
