package pt.aguiarvieira.psacc.ui.feature.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import pt.aguiarvieira.psacc.domain.model.DoorLockState
import pt.aguiarvieira.psacc.domain.model.LatLng
import pt.aguiarvieira.psacc.domain.model.VehiclePosition
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.ui.components.CarLocationMap
import pt.aguiarvieira.psacc.ui.components.FullScreenMapDialog
import pt.aguiarvieira.psacc.ui.components.ShapedIcon
import pt.aguiarvieira.psacc.ui.components.mapsAvailable
import pt.aguiarvieira.psacc.ui.components.StatTile
import pt.aguiarvieira.psacc.util.Formatters
import java.util.Locale
import kotlin.coroutines.resume

private data class Tile(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val supporting: String? = null,
    val shape: Shape,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusGrid(status: VehicleStatus, batterySoh: Double?, lengthUnit: String, modifier: Modifier = Modifier) {
    val tiles = buildList {
        add(
            Tile(
                Icons.Filled.Timeline, "Odometer", Formatters.distance(status.odometerKm, lengthUnit),
                shape = MaterialShapes.Cookie6Sided.toShape(),
            ),
        )
        status.outsideTempC?.let {
            add(
                Tile(
                    Icons.Filled.Thermostat, "Outside", Formatters.temperature(it),
                    supporting = status.isDay?.let { day -> if (day) "Daytime" else "Night" },
                    shape = MaterialShapes.Sunny.toShape(),
                ),
            )
        }
        val health = batterySoh ?: status.electric?.batteryHealthPercent
        health?.let {
            add(
                Tile(
                    Icons.Filled.Battery5Bar, "Battery health", Formatters.percent(it),
                    supporting = if (batterySoh != null) "State of health" else "Reported by car",
                    shape = MaterialShapes.Pill.toShape(),
                ),
            )
        }
        status.auxBatteryVoltage?.let {
            // PSA reports the 12 V battery as a charge percentage despite the field name.
            add(Tile(Icons.Filled.BatteryStd, "12 V battery", Formatters.percent(it), shape = MaterialShapes.Square.toShape()))
        }
        status.ignition?.let {
            add(
                Tile(
                    Icons.Filled.Key, "Ignition", ignitionText(it),
                    supporting = status.speed?.takeIf { status.moving == true }?.let { s -> "${s.toInt()} km/h" },
                    shape = MaterialShapes.Clover4Leaf.toShape(),
                ),
            )
        }
        if (status.doorLock != null || status.openDoors.isNotEmpty()) {
            add(
                Tile(
                    Icons.Filled.MeetingRoom, "Doors",
                    when (status.doorLock) {
                        DoorLockState.Locked -> "Locked"
                        DoorLockState.Unlocked -> "Unlocked"
                        DoorLockState.Partial -> "Partly locked"
                        null -> "—"
                    },
                    supporting = if (status.openDoors.isEmpty()) "All closed" else "${status.openDoors.size} open",
                    shape = MaterialShapes.Arch.toShape(),
                ),
            )
        }
        status.moving?.takeIf { it && status.ignition == null }?.let {
            add(Tile(Icons.Filled.Speed, "Moving", Formatters.distance(status.speed, "km/h"), shape = MaterialShapes.Gem.toShape()))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    StatTile(
                        icon = t.icon,
                        label = t.label,
                        value = t.value,
                        supporting = t.supporting,
                        shape = t.shape,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun ignitionText(raw: String) = when (raw) {
    "Stop" -> "Off"
    "StartUp" -> "Starting"
    "Start" -> "On"
    "Free" -> "Accessory"
    else -> raw
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocationCard(position: VehiclePosition, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val address by produceState<String?>(null, position.latitude, position.longitude) {
        value = reverseGeocode(context, position.latitude, position.longitude)
    }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val latLng = LatLng(position.latitude, position.longitude)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        if (mapsAvailable) {
            CarLocationMap(
                position = latLng,
                onClick = { fullScreen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .clip(MaterialTheme.shapes.large),
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                if (!mapsAvailable) {
                    ShapedIcon(
                        icon = Icons.Filled.LocationOn,
                        shape = MaterialShapes.Ghostish.toShape(),
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        size = 48.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = address ?: "%.5f, %.5f".format(Locale.US, position.latitude, position.longitude),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val details = buildList {
                        if (address != null) add("%.5f, %.5f".format(Locale.US, position.latitude, position.longitude))
                        position.altitude?.let { add("${it.toInt()} m") }
                    }.joinToString(" · ")
                    if (details.isNotEmpty()) {
                        Text(details, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Seen ${Formatters.relative(position.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = { openInMaps(context, position.latitude, position.longitude) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Open in Maps")
            }
        }
    }

    if (fullScreen) {
        FullScreenMapDialog(
            title = address ?: "Car location",
            onDismiss = { fullScreen = false },
            onOpenExternal = { openInMaps(context, position.latitude, position.longitude) },
        ) { mapModifier ->
            CarLocationMap(position = latLng, interactive = true, modifier = mapModifier)
        }
    }
}

fun openInMaps(context: Context, lat: Double, lng: Double, label: String = "Car") {
    val uri = "geo:$lat,$lng?q=$lat,$lng(${android.net.Uri.encode(label)})".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng".toUri()))
    }
}

/** Best-effort street address; null when no geocoder backend is available or the lookup fails. */
private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context)
    val addresses: List<Address>? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    lat, lng, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }
        }
    } catch (_: Exception) {
        null
    }
    val a = addresses?.firstOrNull() ?: return null
    return listOfNotNull(
        listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").takeIf { it.isNotBlank() },
        a.locality ?: a.subAdminArea,
    ).joinToString(", ").takeIf { it.isNotBlank() } ?: a.getAddressLine(0)
}
