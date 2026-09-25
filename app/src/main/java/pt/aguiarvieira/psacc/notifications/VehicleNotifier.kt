package pt.aguiarvieira.psacc.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import pt.aguiarvieira.psacc.MainActivity
import pt.aguiarvieira.psacc.R
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.util.Formatters
import javax.inject.Inject
import javax.inject.Singleton

/** Turns [VehicleEvent]s into Android notifications, one channel per [NotificationCategory]. */
@Singleton
class VehicleNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            NotificationCategory.entries.map { category ->
                NotificationChannel(category.channelId, category.title, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = category.description }
            },
        )
    }

    fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(vehicle: Vehicle, events: List<VehicleEvent>, settings: ServerSettings) {
        if (events.isEmpty() || !canPost()) return
        val manager = NotificationManagerCompat.from(context)
        events.forEach { event ->
            val content = describe(vehicle.displayName, event, settings)
            val notification = NotificationCompat.Builder(context, event.category.channelId)
                .setSmallIcon(R.drawable.ic_stat_car)
                .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
                .setContentIntent(openTabIntent(event.category))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .apply { content.whenMillis?.let { setWhen(it).setShowWhen(true) } }
                .build()
            // Permission was checked in canPost(); the explicit check keeps lint satisfied.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(notificationId(vehicle.vin, event), notification)
            }
        }
    }

    private data class Content(val title: String, val text: String, val whenMillis: Long? = null)

    private fun describe(name: String, event: VehicleEvent, settings: ServerSettings): Content = when (event) {
        is VehicleEvent.TripStarted -> {
            val t = event.trip
            Content(
                title = "$name: trip in progress",
                text = listOfNotNull(
                    "Started at ${Formatters.time(t.startAt)}",
                    t.distance?.takeIf { it > 0 }?.let {
                        "${Formatters.distance(it, settings.lengthUnit, decimals = 1)} so far"
                    },
                    t.startBatteryPercent?.let { "battery ${Formatters.percent(it)} at the start" },
                ).joinToString(" · "),
                whenMillis = t.startAt?.toEpochMilli(),
            )
        }
        is VehicleEvent.TripRecorded -> {
            val t = event.trip
            Content(
                title = "$name: trip recorded",
                text = listOfNotNull(
                    Formatters.distance(t.distance, settings.lengthUnit, decimals = 1),
                    t.duration?.let { Formatters.duration(it) },
                    t.kwhPer100?.takeIf { it > 0 }?.let { "${Formatters.number(it, 1)} kWh/100 ${settings.lengthUnit}" },
                    t.litresPer100?.takeIf { it > 0 }?.let { "${Formatters.number(it, 1)} L/100 ${settings.lengthUnit}" },
                ).joinToString(" · "),
                whenMillis = (t.endAt ?: t.startAt)?.toEpochMilli(),
            )
        }
        is VehicleEvent.IgnitionChanged -> Content(
            title = if (event.on) "$name was started" else "$name was turned off",
            text = levelSummary(event.status, settings),
        )
        is VehicleEvent.PlugChanged -> {
            val charging = event.status.electric?.charging
            Content(
                title = if (event.plugged) "$name was plugged in" else "$name was unplugged",
                text = if (event.plugged && charging?.status != ChargeStatus.InProgress) {
                    Formatters.timeOfDay(charging?.scheduledStart)?.let { "Charging starts at $it · ${batteryText(event.status)}" }
                        ?: batteryText(event.status)
                } else {
                    batteryText(event.status)
                },
            )
        }
        is VehicleEvent.ChargingChanged -> {
            val charging = event.status.electric?.charging
            when (event.to) {
                ChargeStatus.InProgress -> Content(
                    "$name is charging",
                    listOfNotNull(
                        batteryText(event.status),
                        charging?.remaining?.let { "${Formatters.duration(it)} left" },
                    ).joinToString(" · "),
                )
                ChargeStatus.Finished -> Content("$name finished charging", batteryText(event.status))
                ChargeStatus.Stopped -> Content("$name stopped charging", batteryText(event.status))
                ChargeStatus.Failure -> Content("$name: charging failed", batteryText(event.status))
                else -> Content("$name: charging ${event.to.name.lowercase()}", batteryText(event.status))
            }
        }
        is VehicleEvent.SessionFinished -> {
            val s = event.session
            Content(
                title = "$name: charging session recorded",
                text = listOfNotNull(
                    if (s.startLevel != null || s.endLevel != null) {
                        "${Formatters.percent(s.startLevel)} → ${Formatters.percent(s.endLevel)}"
                    } else {
                        null
                    },
                    s.energy?.let { "${Formatters.number(it, 1)} kWh" },
                    s.price?.let { Formatters.money(it, settings.currency) },
                    s.duration?.let { Formatters.duration(it) },
                ).joinToString(" · "),
                whenMillis = s.stopAt?.toEpochMilli(),
            )
        }
    }

    private fun batteryText(status: VehicleStatus): String =
        status.electric?.levelPercent?.let { "Battery at ${Formatters.percent(it)}" }
            ?: status.fuel?.levelPercent?.let { "Fuel at ${Formatters.percent(it)}" }
            ?: "Tap to see the car's status"

    private fun levelSummary(status: VehicleStatus, settings: ServerSettings): String = listOfNotNull(
        status.electric?.levelPercent?.let {
            "Battery ${Formatters.percent(it)} (${Formatters.distance(status.electric.rangeKm, settings.lengthUnit)})"
        },
        status.fuel?.levelPercent?.let {
            "fuel ${Formatters.percent(it)} (${Formatters.distance(status.fuel.rangeKm, settings.lengthUnit)})"
        },
    ).joinToString(" · ").ifEmpty { "Tap to see the car's status" }

    /**
     * State-like events (ignition, plug, charge status) replace the vehicle's previous notification of
     * the same kind; records (trips, sessions) each get their own. A trip's final notification replaces
     * (and re-alerts over) the one posted when it started.
     */
    private fun notificationId(vin: String, event: VehicleEvent): Int = when (event) {
        is VehicleEvent.TripStarted -> "trip:$vin:${event.trip.startAt}"
        is VehicleEvent.TripRecorded -> "trip:$vin:${event.trip.startAt}"
        is VehicleEvent.SessionFinished -> "session:$vin:${event.session.startAt}"
        is VehicleEvent.IgnitionChanged -> "ignition:$vin"
        is VehicleEvent.PlugChanged -> "plug:$vin"
        is VehicleEvent.ChargingChanged -> "charging:$vin"
    }.hashCode()

    private fun openTabIntent(category: NotificationCategory): PendingIntent {
        val tab = when (category) {
            NotificationCategory.TRIPS -> MainActivity.TAB_TRIPS
            NotificationCategory.CHARGING_SESSIONS -> MainActivity.TAB_CHARGING
            NotificationCategory.IGNITION, NotificationCategory.CHARGING -> MainActivity.TAB_CAR
        }
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
        return PendingIntent.getActivity(
            context,
            tab.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
