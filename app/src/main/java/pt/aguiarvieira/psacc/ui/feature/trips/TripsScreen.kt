package pt.aguiarvieira.psacc.ui.feature.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.psacc.domain.model.LatLng
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.domain.model.Trip
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.ErrorState
import pt.aguiarvieira.psacc.ui.components.FullScreenMapDialog
import pt.aguiarvieira.psacc.ui.components.TripRouteMap
import pt.aguiarvieira.psacc.ui.components.mapsAvailable
import pt.aguiarvieira.psacc.ui.components.FullScreenLoading
import pt.aguiarvieira.psacc.ui.components.MessageState
import pt.aguiarvieira.psacc.ui.components.SectionHeader
import pt.aguiarvieira.psacc.ui.components.ShapedIcon
import pt.aguiarvieira.psacc.ui.components.StatTile
import pt.aguiarvieira.psacc.ui.feature.dashboard.openInMaps
import pt.aguiarvieira.psacc.util.Formatters
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    isPrimaryVehicle: Boolean,
    viewModel: TripsViewModel = hiltViewModel(),
) {
    if (!isPrimaryVehicle) {
        MessageState(
            icon = Icons.Filled.Route,
            title = "Trips are for your first car only",
            body = "PSA Car Controller only records trips for the first vehicle on the account.",
        )
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openTripId by rememberSaveable { mutableStateOf<Int?>(null) }

    when (val trips = state.trips) {
        ContentState.Loading -> FullScreenLoading()
        is ContentState.Error -> ErrorState(trips.message, onRetry = viewModel::retry)
        is ContentState.Data -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (trips.value.isEmpty()) {
                MessageState(
                    icon = Icons.Filled.Route,
                    title = "No trips yet",
                    body = "Trips appear once PSA Car Controller has recorded some driving. Recording must be enabled on the server.",
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.summary?.let { summary ->
                            item(key = "summary") { SummaryCard(summary, state.settings) }
                        }
                        trips.value.forEach { day ->
                            item(key = "day-${day.date}") {
                                SectionHeader(day.date?.let { Formatters.day(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) } ?: "Unknown date")
                            }
                            items(day.trips, key = { "trip-${it.id}" }) { trip ->
                                TripCard(trip, state.settings, onClick = { openTripId = trip.id })
                            }
                        }
                    }
                }
            }
        }
    }

    val openTrip = (state.trips as? ContentState.Data)?.value?.flatMap { it.trips }?.firstOrNull { it.id == openTripId }
    if (openTrip != null) {
        ModalBottomSheet(onDismissRequest = { openTripId = null }) {
            TripDetail(openTrip, state.settings)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryCard(summary: TripsSummary, settings: ServerSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                Formatters.distance(summary.totalDistance, settings.lengthUnit),
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            Text("driven over ${summary.count} ${if (summary.count == 1) "trip" else "trips"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                summary.averageKwhPer100?.let {
                    Metric("${Formatters.number(it, 1)} kWh", "per 100 ${settings.lengthUnit}")
                }
                if (summary.totalEnergyKwh > 0) {
                    Metric("${Formatters.number(summary.totalEnergyKwh, 1)} kWh", "electricity used")
                }
                summary.averageLitresPer100?.let {
                    Metric("${Formatters.number(it, 1)} L", "per 100 ${settings.lengthUnit}")
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripCard(trip: Trip, settings: ServerSettings, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (trip.route.size >= 2) {
                RouteSketch(
                    route = trip.route,
                    modifier = Modifier.size(56.dp),
                    strokeWidthDp = 3f,
                )
            } else {
                ShapedIcon(Icons.Filled.Route, shape = MaterialShapes.Cookie7Sided.toShape(), size = 56.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Formatters.time(trip.startAt)} – ${Formatters.time(trip.endAt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Formatters.distance(trip.distance, settings.lengthUnit, decimals = 1),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    listOfNotNull(
                        Formatters.duration(trip.duration),
                        trip.kwhPer100?.takeIf { it > 0 }?.let { "${Formatters.number(it, 1)} kWh/100" },
                        trip.litresPer100?.takeIf { it > 0 }?.let { "${Formatters.number(it, 1)} L/100" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TripDetail(trip: Trip, settings: ServerSettings) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Formatters.dateTime(trip.startAt), style = MaterialTheme.typography.labelLarge)
        Text(
            Formatters.distance(trip.distance, settings.lengthUnit, decimals = 1),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        var fullScreenMap by rememberSaveable { mutableStateOf(false) }
        if (mapsAvailable && trip.route.isNotEmpty()) {
            TripRouteMap(
                route = trip.route,
                onClick = { fullScreenMap = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            if (fullScreenMap) {
                val end = trip.route.last()
                FullScreenMapDialog(
                    title = Formatters.dateTime(trip.startAt),
                    onDismiss = { fullScreenMap = false },
                    onOpenExternal = { openInMaps(context, end.latitude, end.longitude, label = "Trip end") },
                ) { mapModifier ->
                    TripRouteMap(route = trip.route, interactive = true, modifier = mapModifier)
                }
            }
        } else if (trip.route.size >= 2) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                RouteSketch(
                    route = trip.route,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .padding(24.dp),
                    strokeWidthDp = 5f,
                )
            }
        }
        val tiles = buildList {
            add(Triple(Icons.Filled.Timelapse, "Duration", Formatters.duration(trip.duration)))
            trip.averageSpeed?.let { add(Triple(Icons.Filled.Speed, "Average speed", "${it.toInt()} ${settings.lengthUnit}/h")) }
            trip.kwhPer100?.takeIf { it > 0 }?.let {
                add(Triple(Icons.Filled.BatteryChargingFull, "Electric", "${Formatters.number(it, 1)} kWh/100"))
            }
            trip.energyKwh?.takeIf { it > 0 }?.let {
                add(Triple(Icons.Filled.BatteryChargingFull, "Energy used", "${Formatters.number(it, 2)} kWh"))
            }
            trip.litresPer100?.takeIf { it > 0 }?.let {
                add(Triple(Icons.Filled.LocalGasStation, "Fuel", "${Formatters.number(it, 1)} L/100"))
            }
            trip.temperatureC?.let { add(Triple(Icons.Filled.Thermostat, "Temperature", Formatters.temperature(it))) }
            trip.altitudeDiff?.let { add(Triple(Icons.Filled.Height, "Elevation change", "${if (it > 0) "+" else ""}${it.toInt()} m")) }
            trip.odometer?.let { add(Triple(Icons.Filled.Timeline, "Odometer", Formatters.distance(it, settings.lengthUnit))) }
        }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (icon, label, value) ->
                    StatTile(icon, label, value, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        trip.route.lastOrNull()?.let { end ->
            FilledTonalButton(
                onClick = { openInMaps(context, end.latitude, end.longitude, label = "Trip end") },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Show destination in Maps")
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

/**
 * The trip's GPS trace drawn as a line, scaled to fit. No map tiles — just the shape of the route,
 * with a dot at the start and a larger one at the destination.
 */
@Composable
fun RouteSketch(route: List<LatLng>, modifier: Modifier = Modifier, strokeWidthDp: Float = 4f) {
    val line = MaterialTheme.colorScheme.primary
    val start = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.semantics { contentDescription = "Route shape" }) {
        val minLat = route.minOf { it.latitude }
        val maxLat = route.maxOf { it.latitude }
        val minLng = route.minOf { it.longitude }
        val maxLng = route.maxOf { it.longitude }
        // Equirectangular projection: shrink longitude by cos(latitude) so shapes aren't stretched.
        val lngScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = max((maxLng - minLng) * lngScale, 1e-6)
        val spanY = max(maxLat - minLat, 1e-6)
        val scale = minOf(size.width / spanX, size.height / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2
        fun project(p: LatLng) = Offset(
            x = (offsetX + (p.longitude - minLng) * lngScale * scale).toFloat(),
            y = (offsetY + (maxLat - p.latitude) * scale).toFloat(),
        )
        val strokePx = strokeWidthDp.dp.toPx()
        val path = Path().apply {
            route.forEachIndexed { i, p ->
                val o = project(p)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(path, line, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(start, radius = strokePx * 1.2f, center = project(route.first()))
        drawCircle(line, radius = strokePx * 1.8f, center = project(route.last()))
    }
}
