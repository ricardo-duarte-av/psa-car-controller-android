package pt.aguiarvieira.psacc.data.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the PSA Car Controller daemon lives and the HTTP basic-auth credentials of the reverse proxy
 * in front of it. PSACC itself has no authentication; blank [username] and [password] mean "send no
 * Authorization header".
 */
data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val hasCredentials: Boolean get() = username.isNotEmpty() || password.isNotEmpty()

    /** True when credentials would be sent in the clear. */
    val isInsecure: Boolean get() = baseUrl.startsWith("http://", ignoreCase = true) && hasCredentials

    companion object {
        /**
         * Normalises what a user typed into the URL field: trims, assumes https:// when no scheme is
         * given, and drops trailing slashes. PSACC may sit under a sub-path behind a reverse proxy
         * (`https://host/psacc`), which is preserved. Returns null when it isn't a usable http(s) URL.
         */
        fun normalizeUrl(input: String): String? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "https://$s"
            val url = s.toHttpUrlOrNull() ?: return null
            if (url.host.isBlank()) return null
            return url.toString().trimEnd('/')
        }
    }
}

/** The base URL as an [HttpUrl] whose path ends in "/" so relative segments append under it. */
internal fun ServerConfig.httpUrl(): HttpUrl {
    val url = requireNotNull("$baseUrl/".toHttpUrlOrNull()) { "Invalid server URL: $baseUrl" }
    return url
}
