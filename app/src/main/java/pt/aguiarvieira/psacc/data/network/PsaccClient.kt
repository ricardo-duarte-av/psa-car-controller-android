package pt.aguiarvieira.psacc.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import pt.aguiarvieira.psacc.data.auth.httpUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin HTTP layer over PSA Car Controller's API, GET-only but for the edits the fork accepts.
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
        val url = url(config, pathSegments, query)
        return decode(deserializer, execute(config, Request.Builder().url(url)))
    }

    /** Sends [body] as json with PATCH and decodes the reply. */
    suspend fun <T> patch(
        config: ServerConfig,
        deserializer: DeserializationStrategy<T>,
        pathSegments: List<String>,
        body: JsonElement,
    ): T {
        val request = Request.Builder()
            .url(url(config, pathSegments, emptyMap()))
            .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        return decode(deserializer, execute(config, request, readErrors = true))
    }

    suspend fun getJson(
        config: ServerConfig,
        pathSegments: List<String>,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = get(config, JsonElement.serializer(), pathSegments, query)

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T = try {
        json.decodeFromString(deserializer, body)
    } catch (e: SerializationException) {
        throw PsaccException.BadResponse(e)
    } catch (e: IllegalArgumentException) {
        throw PsaccException.BadResponse(e)
    }

    private fun url(config: ServerConfig, pathSegments: List<String>, query: Map<String, String>) =
        config.httpUrl().newBuilder().apply {
            // addPathSegment percent-encodes, so a VIN (or anything else) can't escape its segment.
            pathSegments.forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    /** [readErrors]: a 4xx `{"error": "..."}` becomes a [PsaccException.Server] with its message. */
    private suspend fun execute(config: ServerConfig, builder: Request.Builder, readErrors: Boolean = false): String {
        val request = builder
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
                !it.isSuccessful -> throw (if (readErrors) serverError(it) else null) ?: PsaccException.Http(it.code)
                else -> try {
                    it.body.string()
                } catch (e: IOException) {
                    throw PsaccException.Network(e)
                }
            }
        }
    }

    /** The fork's edits refuse a request with a 4xx and `{"error": "..."}`, whose message says why. */
    private fun serverError(response: Response): PsaccException.Server? {
        val body = runCatching { response.body.string() }.getOrNull() ?: return null
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull }
            .getOrNull()
        return error?.let { PsaccException.Server(it) }
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
