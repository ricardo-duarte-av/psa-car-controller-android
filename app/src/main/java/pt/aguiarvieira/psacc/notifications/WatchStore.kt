package pt.aguiarvieira.psacc.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "psacc_watch")

/** Persists [VehicleWatch] per VIN between background checks. */
@Singleton
class WatchStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val serializer = MapSerializer(String.serializer(), VehicleWatch.serializer())

    suspend fun load(): Map<String, VehicleWatch> {
        val raw = context.watchDataStore.data.first()[KEY] ?: return emptyMap()
        // A corrupt or schema-incompatible blob just means re-baselining, never a crash.
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    suspend fun save(watches: Map<String, VehicleWatch>) {
        context.watchDataStore.edit { it[KEY] = json.encodeToString(serializer, watches) }
    }

    suspend fun clear() {
        context.watchDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("vehicle_watches")
    }
}
