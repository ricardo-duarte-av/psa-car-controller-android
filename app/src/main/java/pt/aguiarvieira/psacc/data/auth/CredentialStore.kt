package pt.aguiarvieira.psacc.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the [ServerConfig] in storage whose contract [ConnectionRepositoryImpl] depends on. */
interface ConfigStore {
    fun load(): ServerConfig?
    fun save(config: ServerConfig)
    fun clear()
}

/**
 * Keeps the server URL and basic-auth password in [EncryptedSharedPreferences] so the password is
 * never on disk in plain text. Excluded from backups (see res/xml/backup_rules.xml).
 */
// androidx.security.crypto is deprecated with no Jetpack successor yet; it remains the standard for
// encrypted key/value storage (same choice as the sibling jellymusic app).
@Suppress("DEPRECATION")
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConfigStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun load(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        return ServerConfig(
            baseUrl = url,
            username = prefs.getString(KEY_USER, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        )
    }

    override fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "psacc_credentials"
        const val KEY_URL = "server_url"
        const val KEY_USER = "username"
        const val KEY_PASSWORD = "password"
    }
}
