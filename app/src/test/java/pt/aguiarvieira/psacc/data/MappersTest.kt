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
