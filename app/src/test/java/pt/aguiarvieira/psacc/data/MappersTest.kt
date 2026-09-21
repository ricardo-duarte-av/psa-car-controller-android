package pt.aguiarvieira.psacc.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.dto.MaintenanceDto
import pt.aguiarvieira.psacc.data.network.dto.PsaTripDto
import pt.aguiarvieira.psacc.data.network.dto.ServerSettingsDto
import pt.aguiarvieira.psacc.data.network.dto.TripDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleDto
import pt.aguiarvieira.psacc.data.network.dto.VehicleStatusDto
import pt.aguiarvieira.psacc.data.repository.lockStateOf
import pt.aguiarvieira.psacc.data.repository.parseChargeControl
import pt.aguiarvieira.psacc.data.repository.requireCommandSuccess
import pt.aguiarvieira.psacc.data.repository.toDomain
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.DoorLockState
import pt.aguiarvieira.psacc.domain.model.HourMinute
import java.time.Duration
import java.time.Instant

class MappersTest {

    @Test
    fun `vehicle list decodes`() {
        val vehicles = Fixtures.json.decodeFromString(ListSerializer(VehicleDto.serializer()), Fixtures.vehicles)
            .map { it.toDomain() }
        assertEquals(1, vehicles.size)
        assertEquals(Fixtures.VIN, vehicles[0].vin)
        assertEquals("508 SW Hybrid", vehicles[0].displayName)
        assertEquals(11.5, vehicles[0].batteryKwh!!, 0.0)
    }

    @Test
    fun `hybrid vehicle status maps electric and fuel energy`() {
        val status = Fixtures.json.decodeFromString(VehicleStatusDto.serializer(), Fixtures.vehicleInfo).toDomain()

        val electric = status.electric!!
        assertEquals(75.0, electric.levelPercent!!, 0.0)
        assertEquals(30.0, electric.rangeKm!!, 0.0)
        assertEquals(92.0, electric.batteryHealthPercent!!, 0.0)
        assertEquals(ChargeStatus.Disconnected, electric.charging!!.status)
        assertFalse(electric.charging.plugged)
        assertEquals(Duration.ofHours(2), electric.charging.scheduledStart)

        assertEquals(46.0, status.fuel!!.levelPercent!!, 0.0)
        assertEquals(175.0, status.fuel.rangeKm!!, 0.0)

        assertEquals(142717.4, status.odometerKm!!, 0.0)
        assertEquals(22.0, status.outsideTempC!!, 0.0)
        assertEquals("Stop", status.ignition)
        assertEquals(38.7223, status.position!!.latitude, 0.0)
        assertEquals(-9.1393, status.position.longitude, 0.0)
        assertEquals(93.0, status.position.altitude!!, 0.0)
        assertFalse(status.preconditioning!!.active)
        assertNull(status.doorLock)
        assertEquals(Instant.parse("2026-09-16T10:12:00Z"), status.updatedAt)
    }

    @Test
    fun `empty status object maps to all-null status`() {
        val status = Fixtures.json.decodeFromString(VehicleStatusDto.serializer(), "{}").toDomain()
        assertNull(status.electric)
        assertNull(status.fuel)
        assertNull(status.position)
        assertTrue(status.openDoors.isEmpty())
    }

    @Test
    fun `trip decodes with minutes duration and route`() {
        val trip = Fixtures.json.decodeFromString(ListSerializer(TripDto.serializer()), Fixtures.trips)
            .mapIndexed { i, t -> t.toDomain(i) }
            .single()
        assertEquals(1, trip.id)
        assertEquals(Instant.parse("2026-09-16T08:21:15Z"), trip.startAt)
        assertEquals(Duration.ofSeconds(468), trip.duration)
        assertEquals(3, trip.route.size)
        assertEquals(23.0, trip.kwhPer100!!, 0.0)
    }

    @Test
    fun `psa trip decodes with levels at both ends and seconds duration`() {
        val body = """
            [{"id":"abc","startedAt":"2026-09-12T09:49:48Z","stoppedAt":"2026-09-12T09:55:00Z",
              "duration":312,"distance":3.1,"startMileage":142557.5,
              "startEnergies":[{"type":"Fuel","level":63.0,"autonomy":290},
                               {"type":"Electric","level":95.0,"autonomy":40}],
              "endEnergies":[{"type":"Fuel","level":63.0},{"type":"Electric","level":78.0}],
              "energyConsumptions":[{"type":"Fuel","consumption":32.184,"avgConsumption":156.995}],
              "kinetic":{"avgSpeed":16.53,"maxSpeed":0.0},
              "startPosition":{"geometry":{"coordinates":[-9.14,38.72]}},
              "stopPosition":{"geometry":{"coordinates":[-9.12,38.74]}}}]
        """.trimIndent()
        val trip = Fixtures.json.decodeFromString(ListSerializer(PsaTripDto.serializer()), body)
            .mapIndexed { i, dto -> dto.toDomain(i) }
            .single()

        assertEquals(Instant.parse("2026-09-12T09:49:48Z"), trip.startAt)
        // seconds here, where PSACC's own trips are in minutes
        assertEquals(Duration.ofSeconds(312), trip.duration)
        assertEquals(95.0, trip.startBatteryPercent!!, 0.0)
        assertEquals(78.0, trip.endBatteryPercent!!, 0.0)
        assertEquals(63.0, trip.startFuelPercent!!, 0.0)
        // PSA copies the start level into endEnergies, so an identical value means "not reported"
        assertNull(trip.endFuelPercent)
        // centilitres, checked against the car's trip computer (0.3 L / 1.4 L/100 km)
        assertEquals(1.57, trip.litresPer100!!, 0.01)
        assertEquals(0.32, trip.fuelLitres!!, 0.01)
        // PSA sends no Electric entry; those figures come from PSACC's own trips
        assertNull(trip.kwhPer100)
        assertNull(trip.energyKwh)
        // m/s in, km/h out: 16.53 m/s is the 59 km/h of a real 20.5 km trip
        assertEquals(59.5, trip.averageSpeed!!, 0.1)
        // maxSpeed is reported as 0.0, i.e. not measured
        assertNull(trip.maxSpeed)
        // two endpoints, no intermediate points: a line, and enough to count as a route
        assertEquals(2, trip.route.size)
        assertTrue(trip.hasRoute)
    }

    @Test
    fun `a psa trip without positions has no route`() {
        val body = """[{"id":"a","startedAt":"2026-09-17T20:13:33Z","duration":600,"distance":2.3}]"""
        val trip = Fixtures.json.decodeFromString(ListSerializer(PsaTripDto.serializer()), body)
            .map { it.toDomain(0) }.single()
        assertTrue(trip.route.isEmpty())
        assertFalse(trip.hasRoute)
        assertNull(trip.startBatteryPercent)
    }

    @Test
    fun `maintenance decodes`() {
        val m = Fixtures.json.decodeFromString(
            MaintenanceDto.serializer(),
            """{"createdAt":"2026-09-18T19:44:12Z","updatedAt":"2026-09-18T19:44:12Z",
               "mileageBeforeMaintenance":29880.0,"daysBeforeMaintenance":348}""",
        ).toDomain()
        assertEquals(348, m.daysBefore)
        assertEquals(29880.0, m.distanceBefore!!, 0.0)
        assertEquals(Instant.parse("2026-09-18T19:44:12Z"), m.updatedAt)
    }

    @Test
    fun `server settings decode`() {
        val s = Fixtures.json.decodeFromString(ServerSettingsDto.serializer(), Fixtures.settings).toDomain()
        assertEquals("€", s.currency)
        assertEquals("km", s.lengthUnit)
        assertEquals(0.15, s.dayPrice!!, 0.0)
        assertNull(s.nightPrice)
    }

    @Test
    fun `lock state collapses PSA flags`() {
        assertEquals(DoorLockState.Locked, lockStateOf(listOf("Locked")))
        assertEquals(DoorLockState.Locked, lockStateOf(listOf("SuperLocked", "Locked")))
        assertEquals(DoorLockState.Unlocked, lockStateOf(listOf("Unlocked")))
        assertEquals(DoorLockState.Partial, lockStateOf(listOf("DriverDoorUnlocked")))
        assertEquals(DoorLockState.Partial, lockStateOf(listOf("Locked", "CargoDoorsUnlocked")))
        assertNull(lockStateOf(emptyList()))
    }

    @Test
    fun `charge control parses threshold and disabled stop hour`() {
        val cc = parseChargeControl(Fixtures.json.parseToJsonElement(Fixtures.chargeControl))!!
        assertEquals(100, cc.thresholdPercent)
        assertNull(cc.stopAt)
    }

    @Test
    fun `charge control parses stop hour`() {
        val cc = parseChargeControl(
            Fixtures.json.parseToJsonElement("""{"percentage_threshold": 80, "_stop_hour": [6, 30]}"""),
        )!!
        assertEquals(80, cc.thresholdPercent)
        assertEquals(HourMinute(6, 30), cc.stopAt)
    }

    @Test
    fun `charge control error means not configured`() {
        assertNull(parseChargeControl(buildJsonObject { put("error", "Charge control not setup") }))
    }

    @Test
    fun `command replies`() {
        requireCommandSuccess(JsonPrimitive(true))
        requireCommandSuccess(JsonNull)
        val e = assertThrows(PsaccException.Server::class.java) {
            requireCommandSuccess(buildJsonObject { put("error", "Wakeup rate limit exceeded") })
        }
        assertEquals("Wakeup rate limit exceeded", e.message)
        assertThrows(PsaccException.Server::class.java) { requireCommandSuccess(JsonPrimitive(false)) }
    }
}
