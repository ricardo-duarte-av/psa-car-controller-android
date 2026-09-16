package pt.aguiarvieira.psacc.data.network

/** Every failure talking to PSACC, already phrased for the user. */
sealed class PsaccException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The reverse proxy rejected the basic-auth credentials. */
    class Unauthorized : PsaccException("Wrong username or password.")

    /** No server configured yet (should not happen past onboarding). */
    class NotConfigured : PsaccException("No PSA Car Controller server is configured.")

    /** Could not reach the host at all (DNS, refused, TLS, timeout...). */
    class Network(cause: Throwable) :
        PsaccException("Couldn't reach the server: ${cause.message ?: cause.javaClass.simpleName}", cause)

    /** Non-2xx other than 401. PSACC answers 500 with an HTML page when the PSA API call throws. */
    class Http(val code: Int) : PsaccException(
        when (code) {
            404 -> "The server doesn't know this request (HTTP 404). Is this a PSA Car Controller URL?"
            in 500..599 -> "PSA Car Controller hit an error (HTTP $code). Check its logs — the PSA " +
                "login may need renewing in its web interface."
            else -> "Unexpected server response (HTTP $code)."
        },
    )

    /** 200, but the body wasn't the JSON we expected (e.g. a login page from a different app). */
    class BadResponse(cause: Throwable?) :
        PsaccException("The server's reply wasn't understood. Is this a PSA Car Controller URL?", cause)

    /** PSACC's own `{"error": "..."}` replies (rate limits, charge control not set up, ...). */
    class Server(serverMessage: String) : PsaccException(serverMessage)
}
