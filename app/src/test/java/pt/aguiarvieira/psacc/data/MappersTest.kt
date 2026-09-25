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
import pt.aguiarvieira.psacc.data.network.dto.ChargingSessionDto
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
import pt.aguiarvieira.psacc.data.repository.toJson
import pt.aguiarvieira.psacc.domain.model.ChargePlace
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ChargingSessionEdit
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
    fun `a psa battery level with no range is dropped unless it is low`() {
        // 23/09/2026: 100% with a range of 0 on a trip driven just before a charge from 5%
        val body = """
            [{"id":"a","startedAt":"2026-09-23T09:34:49Z","duration":724,"distance":6.2,
              "startEnergies":[{"type":"Fuel","level":36.0,"autonomy":165},
                               {"type":"Electric","level":100.0,"autonomy":0}],
              "endEnergies":[{"type":"Fuel","level":35.0,"autonomy":150},
                             {"type":"Electric","level":100.0,"autonomy":0}]},
             {"id":"b","startedAt":"2026-09-15T12:25:34Z","duration":300,"distance":0.8,
              "startEnergies":[{"type":"Electric","level":5.0,"autonomy":0}]},
             {"id":"c","startedAt":"2026-09-23T14:26:46Z","duration":197,"distance":0.2,
              "startEnergies":[{"type":"Electric","level":100.0,"autonomy":42}]}]
        """.trimIndent()
        val (bogus, low, charged) = Fixtures.json.decodeFromString(ListSerializer(PsaTripDto.serializer()), body)
            .mapIndexed { i, dto -> dto.toDomain(i) }

        assertNull(bogus.startBatteryPercent)
        assertNull(bogus.endBatteryPercent)
        assertEquals(36.0, bogus.startFuelPercent!!, 0.0)
        assertEquals(35.0, bogus.endFuelPercent!!, 0.0)
        assertEquals(5.0, low.startBatteryPercent!!, 0.0)
        assertEquals(100.0, charged.startBatteryPercent!!, 0.0)
    }

    @Test
    fun `a merged trip decodes with its levels and fuel`() {
        val body = """
            [{"id":58,"start_at":"Wed, 23 Sep 2026 09:34:49 GMT","end_at":"Wed, 23 Sep 2026 09:46:53 GMT",
              "duration":12.07,"distance":6.2,"mileage":142839.5,"speed_average":30.8,
              "consumption":0.9,"consumption_km":14.5,"consumption_fuel":0.46,"consumption_fuel_km":7.43,
              "consumption_by_temp":null,"altitude_diff":null,"positions":{"lat":[],"long":[]},
              "source":"psa","start_level":60,"end_level":52,"start_level_source":"car",
              "end_level_source":"car","start_level_fuel":36.0,"end_level_fuel":36.0}]
        """.trimIndent()
        val trip = Fixtures.json.decodeFromString(ListSerializer(TripDto.serializer()), body)
            .mapIndexed { i, dto -> dto.toDomain(i) }
            .single()

        assertTrue(trip.merged)
        assertEquals(Duration.ofSeconds(724), trip.duration)
        assertEquals(60.0, trip.startBatteryPercent!!, 0.0)
        assertEquals(52.0, trip.endBatteryPercent!!, 0.0)
        assertEquals(14.5, trip.kwhPer100!!, 0.0)
        assertEquals(0.46, trip.fuelLitres!!, 0.0)
        assertEquals(36.0, trip.startFuelPercent!!, 0.0)
        // PSA copies its start level into the end one
        assertNull(trip.endFuelPercent)
        assertFalse(trip.hasRoute)
    }

    @Test
    fun `the trip being driven is in progress, and a server without the flag reports none`() {
        val merged = """
            [{"id":60,"start_at":"Wed, 23 Sep 2026 19:04:24 GMT","duration":17.3,"distance":5.5,"in_progress":true},
             {"id":59,"start_at":"Wed, 23 Sep 2026 14:26:46 GMT","duration":3.3,"distance":0.2,"in_progress":false},
             {"id":5,"start_at":"Wed, 23 Sep 2026 19:08:42 GMT","duration":17.0,"distance":7.1}]
        """.trimIndent()
        assertEquals(
            listOf(true, false, false),
            Fixtures.json.decodeFromString(ListSerializer(TripDto.serializer()), merged)
                .mapIndexed { i, dto -> dto.toDomain(i).inProgress },
        )
        val psa = """
            [{"id":"a","startedAt":"2026-09-23T19:04:24Z","duration":1038,"distance":5.5,"done":false},
             {"id":"b","startedAt":"2026-09-23T14:26:46Z","duration":197,"distance":0.2,"done":true},
             {"id":"c","startedAt":"2026-09-23T09:34:49Z","duration":724,"distance":6.2}]
        """.trimIndent()
        assertEquals(
            listOf(true, false, false),
            Fixtures.json.decodeFromString(ListSerializer(PsaTripDto.serializer()), psa)
                .mapIndexed { i, dto -> dto.toDomain(i).inProgress },
        )
    }

    @Test
    fun `a psacc trip has no levels and is not merged`() {
        val body = """[{"id":1,"start_at":"Wed, 16 Sep 2026 08:21:15 GMT","duration":7.8,"distance":3.0}]"""
        val trip = Fixtures.json.decodeFromString(ListSerializer(TripDto.serializer()), body)
            .map { it.toDomain(0) }.single()
        assertFalse(trip.merged)
        assertNull(trip.startBatteryPercent)
        assertNull(trip.fuelLitres)
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

    @Test
    fun `a charging session carries what was set by hand`() {
        val json = """[{"start_at": "Thu, 25 Sep 2026 08:32:30 GMT", "stop_at": "Thu, 25 Sep 2026 11:17:26 GMT",
            "kw": 10.58, "price": 10.26, "place": "public", "metered_kw": 8.67, "price_manual": true},
            {"start_at": "Tue, 23 Sep 2026 09:49:14 GMT", "kw": 10.9, "price": 1.83}]"""
        val (edited, stock) = Fixtures.json.decodeFromString(ListSerializer(ChargingSessionDto.serializer()), json)
            .map { it.toDomain() }

        assertEquals(ChargePlace.Public, edited.place)
        assertEquals(8.67, edited.energy!!, 0.0)
        assertTrue(edited.priceManual && edited.edited)
        // a stock daemon, or a session never edited: home, estimated
        assertEquals(ChargePlace.Home, stock.place)
        assertEquals(10.9, stock.energy!!, 0.0)
        assertFalse(stock.priceManual || stock.edited)
    }

    @Test
    fun `an edit sends every key, a null clearing it`() {
        val body = ChargingSessionEdit(ChargePlace.Work, null, 10.26).toJson(Instant.parse("2026-09-25T08:32:30Z"))
        assertEquals(
            buildJsonObject {
                put("start_at", "2026-09-25T08:32:30Z")
                put("place", "work")
                put("metered_kw", JsonNull)
                put("price", 10.26)
            },
            body,
        )
    }
}
