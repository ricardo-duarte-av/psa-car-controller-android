package pt.aguiarvieira.psacc.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

/**
 * Parses the handful of datetime spellings PSACC emits, depending on which Python path produced them:
 *  - `str(datetime)`:      "2026-09-16 10:12:00+00:00", optionally with microseconds / no offset
 *  - ISO 8601:             "2026-09-16T10:12:00Z"
 *  - Flask `jsonify`:      "Wed, 16 Sep 2026 08:21:15 GMT" (trips, charging sessions)
 * Values without an offset are PSA timestamps, which are UTC.
 */
object PsaccTime {

    private val pythonFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd")
        .optionalStart().appendLiteral(' ').optionalEnd()
        .optionalStart().appendLiteral('T').optionalEnd()
        .appendPattern("HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
        .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
        .toFormatter()

    fun parseInstant(value: String?): Instant? {
        val s = value?.trim()?.takeIf { it.isNotEmpty() && it != "None" } ?: return null
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .recoverCatching {
                val parsed = pythonFormatter.parseBest(s, OffsetDateTime::from, LocalDateTime::from)
                when (parsed) {
                    is OffsetDateTime -> parsed.toInstant()
                    is LocalDateTime -> parsed.toInstant(ZoneOffset.UTC)
                    else -> throw DateTimeParseException("Unsupported", s, 0)
                }
            }
            .getOrNull()
    }

    /** ISO-8601 durations as sent by PSA ("PT2H", "PT1H35M"). */
    fun parseDuration(value: String?): Duration? {
        val s = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Duration.parse(s) }.getOrNull()
    }
}
