package pt.aguiarvieira.psacc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.psacc.data.auth.ServerConfig
import java.time.Duration
import java.time.Instant

class PsaccTimeTest {

    @Test
    fun `parses every datetime spelling PSACC emits`() {
        val expected = Instant.parse("2026-09-16T10:12:00Z")
        assertEquals(expected, PsaccTime.parseInstant("2026-09-16 10:12:00+00:00"))
        assertEquals(expected, PsaccTime.parseInstant("2026-09-16 11:12:00+01:00"))
        assertEquals(expected, PsaccTime.parseInstant("2026-09-16T10:12:00Z"))
        assertEquals(expected, PsaccTime.parseInstant("2026-09-16 10:12:00"))
        assertEquals(expected.plusMillis(123), PsaccTime.parseInstant("2026-09-16 10:12:00.123000+00:00"))
        assertEquals(expected, PsaccTime.parseInstant("Wed, 16 Sep 2026 10:12:00 GMT"))
        assertNull(PsaccTime.parseInstant(null))
        assertNull(PsaccTime.parseInstant("None"))
        assertNull(PsaccTime.parseInstant("garbage"))
    }

    @Test
    fun `parses ISO durations`() {
        assertEquals(Duration.ofHours(2), PsaccTime.parseDuration("PT2H"))
        assertEquals(Duration.ofMinutes(95), PsaccTime.parseDuration("PT1H35M"))
        assertNull(PsaccTime.parseDuration(""))
        assertNull(PsaccTime.parseDuration("nope"))
    }

    @Test
    fun `formats durations and relative times`() {
        assertEquals("1 h 05 min", Formatters.duration(Duration.ofMinutes(65)))
        assertEquals("42 min", Formatters.duration(Duration.ofMinutes(42)))
        assertEquals("< 1 min", Formatters.duration(Duration.ofSeconds(20)))
        val now = Instant.parse("2026-09-16T12:00:00Z")
        assertEquals("just now", Formatters.relative(now.minusSeconds(10), now))
        assertEquals("5 min ago", Formatters.relative(now.minusSeconds(300), now))
        assertEquals("3 h ago", Formatters.relative(now.minusSeconds(3 * 3600), now))
        assertEquals("22:30", Formatters.timeOfDay(Duration.parse("PT22H30M")))
    }

    @Test
    fun `normalizes server urls`() {
        assertEquals("https://psa.example.com", ServerConfig.normalizeUrl(" psa.example.com/ "))
        assertEquals("http://192.168.1.5:5000", ServerConfig.normalizeUrl("http://192.168.1.5:5000"))
        assertEquals("https://example.com/psacc", ServerConfig.normalizeUrl("https://example.com/psacc/"))
        assertNull(ServerConfig.normalizeUrl(""))
        assertNull(ServerConfig.normalizeUrl("ftp://example.com"))
    }
}
