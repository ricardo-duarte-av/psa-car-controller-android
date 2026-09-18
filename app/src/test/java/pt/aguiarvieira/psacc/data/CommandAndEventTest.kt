package pt.aguiarvieira.psacc.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.aguiarvieira.psacc.data.network.PsaccException
import pt.aguiarvieira.psacc.data.network.SseFrame
import pt.aguiarvieira.psacc.data.repository.parseCommandReply
import pt.aguiarvieira.psacc.data.repository.toEvent
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.CommandState
import pt.aguiarvieira.psacc.domain.model.PsaccEvent
import pt.aguiarvieira.psacc.domain.model.matchesAction
import java.time.Instant

class CommandAndEventTest {

    private fun reply(body: String) = parseCommandReply(Fixtures.json.parseToJsonElement(body))

    @Test
    fun `stock daemon replies mean sent, nothing more`() {
        assertEquals(CommandState.Sent, reply("true").state)
        // The horn endpoint answers null even when it worked.
        assertEquals(CommandState.Sent, reply("null").state)
        assertEquals("Sent to the car", reply("true").message)
    }

    @Test
    fun `rate limits and refusals are failures`() {
        val rateLimited = assertThrows(PsaccException.Server::class.java) {
            reply("""{"error": "Wakeup rate limit exceeded"}""")
        }
        assertEquals("Wakeup rate limit exceeded", rateLimited.message)
        assertThrows(PsaccException.Server::class.java) { reply("false") }
    }

    @Test
    fun `pending, success and refusal from the forked daemon`() {
        val pending = reply(
            """{"correlation_id":"abc","vin":"V","action":"Doors","status":"pending","return_code":null,
               "reason":null,"message":"command sent, waiting for the car answer",
               "sent_at":"2026-09-17T20:54:39+00:00","updated_at":"2026-09-17T20:54:39+00:00"}""",
        )
        assertEquals(CommandState.Pending, pending.state)
        assertEquals("abc", pending.correlationId)
        assertFalse(pending.settled)
        assertFalse(pending.refused)

        val success = reply(
            """{"correlation_id":"abc","action":"VehCharge/state","status":"success","return_code":"0",
               "message":"command accepted by the car","updated_at":"2026-09-18T08:18:41+00:00"}""",
        )
        assertEquals(CommandState.Success, success.state)
        assertTrue(success.settled)
        assertFalse(success.refused)
        assertEquals(Instant.parse("2026-09-18T08:18:41Z"), success.updatedAt)

        val refused = reply(
            """{"correlation_id":"abc","action":"Doors","status":"failed","return_code":"400",
               "reason":"[authorization.denied.cvs.response.no.matching.service.key]",
               "message":"PSA refused: this service isn't available for this car"}""",
        )
        assertEquals(CommandState.Failed, refused.state)
        assertTrue(refused.settled)
        assertTrue(refused.refused)
    }

    @Test
    fun `a failure that isn't a refusal is not remembered`() {
        val failed = reply("""{"status":"failed","return_code":"1","message":"can't refresh the remote token"}""")
        assertEquals(CommandState.Failed, failed.state)
        assertFalse(failed.refused)
    }

    /** The exact frame captured from a live server (VIN and coordinates aside). */
    private val vehicleFrame = """
        {"type": "vehicle", "date": "2026-09-18T08:44:44.517878+00:00",
         "data": {"vin": "${Fixtures.VIN}", "date": "2026-09-18T08:44:10Z", "battery_level": 53,
                  "autonomy": 24, "charging": false, "charging_rate": 0, "cable_plugged": true,
                  "preconditioning": false, "raw": {"charging_state": {"remaining_time": 635}}}}
    """.trimIndent()

    @Test
    fun `vehicle events are mapped, including the fields we don't trust yet`() {
        val frame = SseFrame("vehicle", Fixtures.json.parseToJsonElement(vehicleFrame) as JsonObject)
        val event = frame.toEvent(Fixtures.json) as PsaccEvent.VehicleUpdate
        assertEquals(Fixtures.VIN, event.vin)
        assertEquals(Instant.parse("2026-09-18T08:44:10Z"), event.at)
        assertEquals(53.0, event.batteryLevel!!, 0.0)
        assertEquals(24.0, event.autonomy!!, 0.0)
        // Reported by the daemon but unverified against reality: parsed, not shown.
        assertEquals(false, event.charging)
        assertEquals(true, event.cablePlugged)
    }

    @Test
    fun `command results arrive on the stream too`() {
        val body = """
            {"type": "command_result", "date": "2026-09-18T08:18:41+00:00",
             "data": {"correlation_id": "abc", "action": "VehCharge/state", "status": "success",
                      "return_code": "0", "message": "command accepted by the car"}}
        """.trimIndent()
        val frame = SseFrame("command_result", Fixtures.json.parseToJsonElement(body) as JsonObject)
        val event = frame.toEvent(Fixtures.json) as PsaccEvent.CommandUpdate
        assertEquals(CommandState.Success, event.outcome.state)
        assertEquals("abc", event.outcome.correlationId)
    }

    @Test
    fun `unknown and malformed frames are ignored`() {
        assertNull(SseFrame("hello", JsonObject(emptyMap())).toEvent(Fixtures.json))
        assertNull(SseFrame("vehicle", JsonObject(emptyMap())).toEvent(Fixtures.json))
    }

    @Test
    fun `daemon actions map back to the command that caused them`() {
        assertTrue(CarCommand.WakeUp.matchesAction("VehCharge/state"))
        assertTrue(CarCommand.Lock(true).matchesAction("Doors"))
        assertTrue(CarCommand.Lights.matchesAction("Lights"))
        assertTrue(CarCommand.Charge(true).matchesAction("VehCharge"))
        // The wakeup topic is a sub-topic of VehCharge: it must not be taken for a charge command.
        assertFalse(CarCommand.Charge(true).matchesAction("VehCharge/state"))
    }
}
