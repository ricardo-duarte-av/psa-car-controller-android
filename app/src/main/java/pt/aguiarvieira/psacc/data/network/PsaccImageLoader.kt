package pt.aguiarvieira.psacc.data.network

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Credentials
import okhttp3.OkHttpClient
import pt.aguiarvieira.psacc.data.auth.ConnectionRepository

/**
 * A Coil [ImageLoader] that sends the daemon's basic-auth on image requests, so car pictures — which
 * the daemon serves behind the same auth as everything else — load through it. Only the configured
 * server is given the credentials; any other host is fetched without them.
 */
fun psaccImageLoader(context: Context, connection: ConnectionRepository): ImageLoader {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val config = connection.config.value
            val request = chain.request()
            val sameHost = config != null &&
                request.url.toString().startsWith(config.baseUrl.trimEnd('/'))
            val authed = if (sameHost && config != null && config.hasCredentials) {
                request.newBuilder()
                    .header("Authorization", Credentials.basic(config.username, config.password))
                    .build()
            } else {
                request
            }
            chain.proceed(authed)
        }
        .build()

    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .crossfade(true)
        .build()
}
