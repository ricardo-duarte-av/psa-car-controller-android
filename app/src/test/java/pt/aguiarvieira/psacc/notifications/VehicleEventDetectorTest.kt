package pt.aguiarvieira.psacc.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ChargingState
import pt.aguiarvieira.psacc.domain.model.ElectricEnergy
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import java.time.Instant

class VehicleEventDetectorTest {

    private val vehicle = Vehicle("VIN1", "Test car", "Peugeot", 11.5, 45.0)

    private fun status(ignition: String? = "Stop", charge: ChargeStatus = ChargeStatus.Disconnected, plugged: Boolean = false) =
        VehicleStatus(
            electric = ElectricEnergy(
                levelPercent = 60.0, rangeKm = 30.0, batteryHealthPercent = null,
                charging = ChargingState(charge, charge.name, plugged, null, null, null, null),
                updatedAt = null,
            ),
            fuel = null, odometerKm = null, outsideTempC = null, isDay = null, ignition = ignition,
            moving = null, speed = null, auxBatteryVoltage = null, position = null, preconditioning = null,
            doorLock = null, openDoors = emptyList(), privacy = null, serviceType = null, updatedAt = null,
        )

    private fun trip(start: String, distance: Double = 5.0, inProgress: Boolean = false) = Trip(
        id = start.hashCode(), startAt = Instant.parse(start), duration = null, distance = distance, odometer = null,
        averageSpeed = null, energyKwh = null, kwhPer100 = null, litresPer100 = null, temperatureC = null,
        altitudeDiff = null, route = emptyList(), inProgress = inProgress,
    )

    private fun session(start: String, stop: String?) = ChargingSession(
        startAt = Instant.parse(start), stopAt = stop?.let(Instant::parse), startLevel = 20.0, endLevel = 80.0,
        kwh = 7.0, price = 1.0, co2 = null, mode = "Slow", odometer = null,
    )

    private fun snapshot(
        status: VehicleStatus? = null,
        trips: List<Trip>? = null,
        sessions: List<ChargingSession>? = null,
    ) = VehicleSnapshot(vehicle, status, trips, sessions)

    /** Runs a sequence of snapshots through the detector, returning the events of the last one. */
    private fun run(vararg snapshots: VehicleSnapshot): List<VehicleEvent> {
        var watch: VehicleWatch? = null
        var events = emptyList<VehicleEvent>()
        snapshots.forEach {
            val d = VehicleEventDetector.detect(watch, it)
            watch = d.watch
            events = d.events
        }
        return events
    }

    @Test
    fun `first check only baselines`() {
        val events = VehicleEventDetector.detect(
            null,
            snapshot(
                status = status(ignition = "Start", charge = ChargeStatus.InProgress, plugged = true),
                trips = listOf(trip("2026-09-16T08:00:00Z")),
                sessions = listOf(session("2026-09-15T20:00:00Z", "2026-09-15T23:00:00Z")),
            ),
        ).events
        assertTrue(events.isEmpty())
    }

    @Test
    fun `ignition on and off`() {
        val on = run(snapshot(status = status("Stop")), snapshot(status = status("Start")))
        assertEquals(listOf(true), on.filterIsInstance<VehicleEvent.IgnitionChanged>().map { it.on })

        val off = run(snapshot(status = status("StartUp")), snapshot(status = status("Free")))
        assertEquals(listOf(false), off.filterIsInstance<VehicleEvent.IgnitionChanged>().map { it.on })

        assertTrue(run(snapshot(status = status("Start")), snapshot(status = status("StartUp"))).isEmpty())
    }

    @Test
    fun `plugging in and starting to charge`() {
        val events = run(
            snapshot(status = status(charge = ChargeStatus.Disconnected, plugged = false)),
            snapshot(status = status(charge = ChargeStatus.InProgress, plugged = true)),
        )
        assertEquals(1, events.filterIsInstance<VehicleEvent.PlugChanged>().size)
        assertEquals(listOf(ChargeStatus.InProgress), events.filterIsInstance<VehicleEvent.ChargingChanged>().map { it.to })
    }

    @Test
    fun `stopped only reported when a charge was running`() {
        val waiting = run(
            snapshot(status = status(charge = ChargeStatus.Disconnected, plugged = false)),
            snapshot(status = status(charge = ChargeStatus.Stopped, plugged = true)),
        )
        assertTrue(waiting.none { it is VehicleEvent.ChargingChanged })

        val stopped = run(
            snapshot(status = status(charge = ChargeStatus.InProgress, plugged = true)),
            snapshot(status = status(charge = ChargeStatus.Stopped, plugged = true)),
        )
        assertEquals(listOf(ChargeStatus.Stopped), stopped.filterIsInstance<VehicleEvent.ChargingChanged>().map { it.to })

        val unplugged = run(
            snapshot(status = status(charge = ChargeStatus.Finished, plugged = true)),
            snapshot(status = status(charge = ChargeStatus.Disconnected, plugged = false)),
        )
        assertEquals(listOf(false), unplugged.filterIsInstance<VehicleEvent.PlugChanged>().map { it.plugged })
        assertTrue(unplugged.none { it is VehicleEvent.ChargingChanged })
    }

    @Test
    fun `new trips are reported oldest first`() {
        val old = trip("2026-09-16T08:00:00Z")
        val events = run(
            snapshot(trips = listOf(old)),
            snapshot(trips = listOf(trip("2026-09-16T18:00:00Z"), trip("2026-09-16T12:00:00Z"), old)),
        )
        assertEquals(
            listOf("2026-09-16T12:00:00Z", "2026-09-16T18:00:00Z"),
            events.filterIsInstance<VehicleEvent.TripRecorded>().map { it.trip.startAt.toString() },
        )
    }

    @Test
    fun `a failed trips request neither baselines nor loses the watermark`() {
        val old = trip("2026-09-16T08:00:00Z")
        val events = run(
            snapshot(trips = listOf(old)),
            snapshot(trips = null),
            snapshot(trips = listOf(trip("2026-09-16T09:00:00Z"), old)),
        )
        assertEquals(1, events.filterIsInstance<VehicleEvent.TripRecorded>().size)
    }

    @Test
    fun `a trip being driven is reported when it starts, then again with its final figures`() {
        val old = trip("2026-09-23T09:34:00Z")
        val start = "2026-09-23T19:04:00Z"
        var watch = VehicleEventDetector.detect(null, snapshot(trips = listOf(old))).watch
        val seen = listOf(3.0, 5.5).map { soFar ->
            val d = VehicleEventDetector.detect(watch, snapshot(trips = listOf(old, trip(start, soFar, inProgress = true))))
            watch = d.watch
            d.events
        }
        assertEquals(listOf(3.0), seen[0].filterIsInstance<VehicleEvent.TripStarted>().map { it.trip.distance })
        assertEquals(1, seen[0].size)
        assertTrue(seen[1].isEmpty())
        val done = VehicleEventDetector.detect(watch, snapshot(trips = listOf(old, trip(start, 7.1))))
        assertEquals(listOf(7.1), done.events.filterIsInstance<VehicleEvent.TripRecorded>().map { it.trip.distance })
        assertEquals(1, done.events.size)
        assertTrue(VehicleEventDetector.detect(done.watch, snapshot(trips = listOf(old, trip(start, 7.1)))).events.isEmpty())
    }

    @Test
    fun `a trip already being driven at the first check is only reported when done`() {
        val start = "2026-09-23T19:04:00Z"
        val first = VehicleEventDetector.detect(null, snapshot(trips = listOf(trip(start, 3.0, inProgress = true))))
        assertTrue(first.events.isEmpty())
        val again = VehicleEventDetector.detect(first.watch, snapshot(trips = listOf(trip(start, 5.0, inProgress = true))))
        assertTrue(again.events.isEmpty())
        val done = VehicleEventDetector.detect(again.watch, snapshot(trips = listOf(trip(start, 7.1))))
        assertEquals(listOf(7.1), done.events.filterIsInstance<VehicleEvent.TripRecorded>().map { it.trip.distance })
    }

    @Test
    fun `a trip that ends and a new one that starts between checks are both reported`() {
        val first = "2026-09-23T19:04:00Z"
        val second = "2026-09-23T20:00:00Z"
        val events = run(
            snapshot(trips = emptyList()),
            snapshot(trips = listOf(trip(first, 3.0, inProgress = true))),
            snapshot(trips = listOf(trip(first, 7.1), trip(second, 1.0, inProgress = true))),
        )
        assertEquals(
            listOf("TripRecorded 2026-09-23T19:04:00Z", "TripStarted 2026-09-23T20:00:00Z"),
            events.map {
                when (it) {
                    is VehicleEvent.TripRecorded -> "TripRecorded ${it.trip.startAt}"
                    is VehicleEvent.TripStarted -> "TripStarted ${it.trip.startAt}"
                    else -> it.toString()
                }
            },
        )
    }

    @Test
    fun `trip flood is capped`() {
        val many = (10..20).map { trip("2026-09-17T$it:00:00Z") }
        val events = run(snapshot(trips = emptyList()), snapshot(trips = many))
        assertEquals(VehicleEventDetector.MAX_TRIP_EVENTS, events.size)
    }

    @Test
    fun `charging session reported when it finishes`() {
        val running = session("2026-09-16T20:00:00Z", null)
        val done = session("2026-09-16T20:00:00Z", "2026-09-16T23:00:00Z")
        val events = run(
            snapshot(sessions = emptyList()),
            snapshot(sessions = listOf(running)),
            snapshot(sessions = listOf(done)),
        )
        assertEquals(1, events.filterIsInstance<VehicleEvent.SessionFinished>().size)

        // Started and finished between two checks.
        val between = run(snapshot(sessions = emptyList()), snapshot(sessions = listOf(done)))
        assertEquals(1, between.filterIsInstance<VehicleEvent.SessionFinished>().size)

        // Already reported: no repeat.
        val repeat = run(snapshot(sessions = emptyList()), snapshot(sessions = listOf(done)), snapshot(sessions = listOf(done)))
        assertTrue(repeat.isEmpty())
    }
}
