package pt.aguiarvieira.psacc.data

import kotlinx.serialization.json.Json

/** Response bodies captured from a live PSACC instance, with the VIN and location replaced. */
object Fixtures {
    const val VIN = "VR3TESTVIN0000001"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val vehicles = """
        [{"vin": "$VIN", "vehicle_id": "abc", "label": "508 SW Hybrid", "brand": "Peugeot",
          "abrp_name": null, "battery_power": 11.5, "fuel_capacity": 45,
          "max_elec_consumption": 70, "max_fuel_consumption": 30}]
    """.trimIndent()

    val vehicleInfo = """
    {
      "embedded": null,
      "links": {"_self": {"href": "https://example.invalid"}},
      "battery": {"current": null, "voltage": 82.0},
      "doors_state": null,
      "energy": [
        {"updated_at": "2026-09-16 10:12:00+00:00", "created_at": "2026-09-16 10:12:00+00:00",
         "autonomy": 30.0, "battery": {"capacity": null, "health": {"capacity": 92.0, "resistance": 0.0}},
         "charging": {"charging_mode": "No", "charging_rate": 0, "next_delayed_time": "PT2H",
                      "plugged": false, "remaining_time": null, "status": "Disconnected"},
         "consumption": null, "level": 75.0, "residual": null, "type": "Electric"},
        {"updated_at": "2026-09-16 10:12:00+00:00", "created_at": "2026-09-16 10:12:00+00:00",
         "autonomy": 175.0, "battery": null, "charging": null, "consumption": null,
         "level": 46.0, "residual": null, "type": "Fuel"}
      ],
      "environment": {"created_at": null, "air": {"temp": 22.0}, "luminosity": {"day": true}},
      "ignition": {"type": "Stop"},
      "kinetic": {"acceleration": null, "moving": null, "pace": null, "speed": null},
      "last_position": {"type": "Feature",
        "geometry": {"coordinates": [-9.1393, 38.7223, 93.0], "type": "Point"},
        "properties": {"heading": null, "signal_quality": 9.0, "type": "Acquire", "updated_at": "2026-09-12 10:08:08+00:00"}},
      "preconditionning": {"air_conditioning": {"failure_cause": null, "status": "Disabled",
        "updated_at": "2026-09-16 10:12:00+00:00", "created_at": "2026-09-16 10:12:00+00:00"}},
      "privacy": {"state": "None"},
      "safety": null,
      "service": {"type": "Hybrid", "updated_at": null},
      "timed_odometer": {"updated_at": "2026-09-16 10:12:00+00:00", "mileage": 142717.4}
    }
    """.trimIndent()

    val trips = """
        [{"altitude_diff": 0, "consumption": 0.69, "consumption_by_temp": 20.0, "consumption_fuel_km": 0,
          "consumption_km": 23.0, "distance": 3.0, "duration": 7.800000000000001, "id": 1,
          "mileage": 142717.4, "positions": {"lat": [38.72, 38.73, 38.74], "long": [-9.14, -9.13, -9.12]},
          "speed_average": 23.076923076923077, "start_at": "Wed, 16 Sep 2026 08:21:15 GMT"}]
    """.trimIndent()

    val settings = """
        {"General": {"currency": "€", "length_unit": "km", "minimum_trip_length": 2.0, "export_format": "csv"},
         "Electricity_config": {"day_price": 0.15, "night_price": null, "night_hour_start": null,
           "night_hour_end": null, "dc_charge_price": null, "high_speed_dc_charge_price": null,
           "high_speed_dc_charge_threshold": null, "charger_efficiency": 0.8942}}
    """.trimIndent()

    val chargeControl = """
        {"_next_stop_hour":null,"_stop_hour":null,"percentage_threshold":100,"retry_count":0,"vin":"$VIN","wakeup_timeout":10}
    """.trimIndent()
}
