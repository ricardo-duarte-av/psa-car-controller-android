package pt.aguiarvieira.psacc.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import pt.aguiarvieira.psacc.data.auth.httpUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin HTTP layer over PSA Car Controller's GET-only API.
 *
 * Every call takes the [ServerConfig] explicitly rather than reading a global, so the connect screen
 * can probe a candidate server before it is saved, using exactly the same code path.
 */
@Singleton
class PsaccClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    suspend fun <T> get(
        config: ServerConfig,
        deserializer: DeserializationStrategy<T>,
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): T {
        val body = getBody(config, pathSegments, query)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            throw PsaccException.BadResponse(e)
        } catch (e: IllegalArgumentException) {
            throw PsaccException.BadResponse(e)
        }
    }

    suspend fun getJson(
        config: ServerConfig,
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = get(config, JsonElement.serializer(), pathSegments, query)

    private suspend fun getBody(
        config: ServerConfig,
        pathSegments: List<String>,
        query: Map<String, String>,
    ): String {
        val url = config.httpUrl().newBuilder().apply {
            // addPathSegment percent-encodes, so a VIN (or anything else) can't escape its segment.
            pathSegments.forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (config.hasCredentials) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }
            .build()

        val response = try {
            okHttpClient.newCall(request).await()
        } catch (e: IOException) {
            throw PsaccException.Network(e)
        }
        return response.use {
            when {
                it.code == 401 || it.code == 403 -> throw PsaccException.Unauthorized()
                !it.isSuccessful -> throw PsaccException.Http(it.code)
                else -> try {
                    it.body.string()
                } catch (e: IOException) {
                    throw PsaccException.Network(e)
                }
            }
        }
    }
}

/** Enqueues the call and suspends until it completes, cancelling the HTTP call with the coroutine. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}
