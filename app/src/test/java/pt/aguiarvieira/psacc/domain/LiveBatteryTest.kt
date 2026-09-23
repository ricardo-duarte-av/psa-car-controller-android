package pt.aguiarvieira.psacc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ChargingState
import pt.aguiarvieira.psacc.domain.model.ElectricEnergy
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.domain.model.withLiveBattery

class LiveBatteryTest {

    // PSA's summary as seen on the test car: 100% while the battery was nearly empty.
    private val status = VehicleStatus(
        electric = ElectricEnergy(
            levelPercent = 100.0, rangeKm = 0.0, batteryHealthPercent = null,
            charging = ChargingState(ChargeStatus.InProgress, "InProgress", true, "Slow", 12.0, null, null),
            updatedAt = null,
        ),
        fuel = null, odometerKm = null, outsideTempC = null, isDay = null, ignition = null,
        moving = null, speed = null, auxBatteryVoltage = null, position = null, preconditioning = null,
        doorLock = null, openDoors = emptyList(), privacy = null, serviceType = null, updatedAt = null,
    )

    private fun live(level: Double?, autonomy: Double?) =
        PsaccEvent.VehicleUpdate("VIN1", null, level, autonomy, true, 12.0, true, false)

    @Test
    fun `live reading replaces level and range but keeps the charge state`() {
        val electric = status.withLiveBattery(live(5.0, 14.0)).electric!!
        assertEquals(5.0, electric.levelPercent!!, 0.0)
        assertEquals(14.0, electric.rangeKm!!, 0.0)
        assertEquals(status.electric!!.charging, electric.charging)
    }

    @Test
    fun `missing range keeps the status range`() {
        assertEquals(0.0, status.withLiveBattery(live(5.0, null)).electric!!.rangeKm!!, 0.0)
    }

    @Test
    fun `no live level leaves the status untouched`() {
        assertSame(status, status.withLiveBattery(null))
        assertSame(status, status.withLiveBattery(live(null, 14.0)))
    }
}
