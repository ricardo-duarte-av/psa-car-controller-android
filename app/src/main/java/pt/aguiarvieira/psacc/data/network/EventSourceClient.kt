package pt.aguiarvieira.psacc.data.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import pt.aguiarvieira.psacc.data.auth.httpUrl
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One frame of the `/events` stream: its SSE event name and its JSON payload. */
data class SseFrame(val event: String, val data: JsonObject)

/**
 * Server-sent events from the forked daemon's `/events`.
 *
 * The stream is long-lived, so it needs its own OkHttp client with no read timeout — the shared one
 * would cut it off after 90 s of silence. The daemon sends a `: keepalive` comment every 30 s, which
 * OkHttp's SSE reader swallows, so a healthy idle stream produces no frames at all.
 *
 * The flow fails on disconnection rather than reconnecting itself; callers decide (the dashboard
 * retries with a backoff while it is on screen).
 */
@Singleton
class EventSourceClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val streamingClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun events(config: ServerConfig): Flow<SseFrame> = callbackFlow {
        val request = Request.Builder()
            .url(config.httpUrl().newBuilder().addPathSegment("events").build())
            .header("Accept", "text/event-stream")
            .apply {
                if (config.hasCredentials) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val payload = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
                if (payload != null) trySend(SseFrame(type.orEmpty(), payload))
            }

            override fun onClosed(eventSource: EventSource) {
                close(PsaccException.Network(java.io.IOException("event stream closed")))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                response?.close()
                val error = when {
                    response?.code == 401 || response?.code == 403 -> PsaccException.Unauthorized()
                    response != null && !response.isSuccessful -> PsaccException.Http(response.code)
                    t is java.io.IOException -> PsaccException.Network(t)
                    else -> PsaccException.BadResponse(t)
                }
                close(error)
            }
        }

        val source = EventSources.createFactory(streamingClient).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }
}
