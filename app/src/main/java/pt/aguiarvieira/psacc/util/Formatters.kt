package pt.aguiarvieira.psacc.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure display formatting, kept free of Android types so it is unit-testable. */
object Formatters {

    fun percent(value: Double?): String = value?.let { "${it.roundToInt()}%" } ?: "—"

    fun distance(value: Double?, unit: String, decimals: Int = 0): String =
        value?.let { "${number(it, decimals)} $unit" } ?: "—"

    fun number(value: Double, decimals: Int = 0): String =
        if (decimals == 0) "%,d".format(Locale.getDefault(), value.roundToInt())
        else "%,.${decimals}f".format(Locale.getDefault(), value)

    fun temperature(value: Double?): String = value?.let { "${it.roundToInt()}°C" } ?: "—"

    fun money(value: Double?, currency: String): String =
        value?.let { "%.2f %s".format(Locale.getDefault(), it, currency) } ?: "—"

    /** "1 h 05 min", "42 min", "under a minute". */
    fun duration(duration: Duration?): String {
        if (duration == null) return "—"
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "$hours h %02d min".format(minutes)
            totalMinutes > 0 -> "$minutes min"
            else -> "< 1 min"
        }
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun relative(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return "unknown"
        val seconds = Duration.between(instant, now).seconds
        val past = seconds >= 0
        val s = abs(seconds)
        val text = when {
            s < 60 -> return "just now"
            s < 3600 -> "${s / 60} min"
            s < 86_400 -> "${s / 3600} h"
            s < 172_800 -> "1 day"
            else -> "${s / 86_400} days"
        }
        return if (past) "$text ago" else "in $text"
    }

    /** A PSA "time of day" duration (PT22H30M) as "22:30". */
    fun timeOfDay(duration: Duration?): String? = duration?.let {
        "%02d:%02d".format(it.toHours() % 24, it.toMinutesPart())
    }

    fun dateTime(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.let { DATE_TIME.withZone(zone).format(it) } ?: "—"

    fun time(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.let { TIME.withZone(zone).format(it) } ?: "—"

    fun day(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        instant?.let { DAY.withZone(zone).format(it) } ?: "Unknown date"

    private val DATE_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
}
