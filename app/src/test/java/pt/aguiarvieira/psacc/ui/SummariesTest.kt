package pt.aguiarvieira.psacc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.ui.feature.charging.ChargingViewModel
import pt.aguiarvieira.psacc.ui.feature.trips.TripsViewModel
import java.time.Instant
import java.time.ZoneOffset

class SummariesTest {

    private fun trip(id: Int, start: String, distance: Double, kwh100: Double?) = Trip(
        id = id, startAt = Instant.parse(start), duration = null, distance = distance, odometer = null,
        averageSpeed = null, energyKwh = kwh100?.let { it * distance / 100 }, kwhPer100 = kwh100,
        litresPer100 = null, temperatureC = null, altitudeDiff = null, route = emptyList(),
    )

    @Test
    fun `trip summary weights consumption by distance`() {
        val trips = listOf(
            trip(1, "2026-09-16T08:00:00Z", 10.0, 20.0),
            trip(2, "2026-09-16T18:00:00Z", 30.0, 12.0),
            trip(3, "2026-09-15T08:00:00Z", 5.0, null),
        )
        val summary = TripsViewModel.summarize(trips)!!
        assertEquals(3, summary.count)
        assertEquals(45.0, summary.totalDistance, 1e-9)
        assertEquals(14.0, summary.averageKwhPer100!!, 1e-9)
        assertNull(summary.averageLitresPer100)

        val days = TripsViewModel.groupByDay(trips, ZoneOffset.UTC)
        assertEquals(listOf(2, 1), days.map { it.trips.size })
    }

    @Test
    fun `charging summary leaves cost null when nothing is priced`() {
        val sessions = listOf(
            ChargingSession(null, null, 20.0, 80.0, 7.0, null, null, "Slow", null),
            ChargingSession(null, null, 50.0, 100.0, 5.5, null, null, "Slow", null),
        )
        val summary = ChargingViewModel.summarize(sessions)!!
        assertEquals(12.5, summary.totalKwh, 1e-9)
        assertNull(summary.totalCost)
        assertNull(ChargingViewModel.summarize(emptyList()))
    }
}
