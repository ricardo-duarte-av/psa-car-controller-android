package pt.aguiarvieira.psacc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import pt.aguiarvieira.psacc.BuildConfig
import pt.aguiarvieira.psacc.domain.model.LatLng
import com.google.android.gms.maps.model.LatLng as GmsLatLng

/** False when the build had no Maps key; callers then show their map-less fallback. */
val mapsAvailable: Boolean get() = BuildConfig.HAS_MAPS_KEY

private fun LatLng.toGms() = GmsLatLng(latitude, longitude)

private const val STREET_ZOOM = 16f

/**
 * Static preview maps use Google's *lite mode*: a bitmap of the map rather than a live GL surface,
 * which is what the Maps SDK recommends inside scrolling lists and sheets (cheap, no gesture fights
 * with the list). A transparent overlay takes the tap so it opens [FullScreenMapDialog] instead of
 * lite mode's default of launching the Google Maps app.
 */
private val liteOptions = { GoogleMapOptions().liteMode(true).mapToolbarEnabled(false) }

private val previewUi = MapUiSettings(
    compassEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = false,
    tiltGesturesEnabled = false,
    zoomControlsEnabled = false,
    zoomGesturesEnabled = false,
)

private val interactiveUi = MapUiSettings(
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    zoomControlsEnabled = false,
)

/** The car's last known position with an expressive car marker. */
@Composable
fun CarLocationMap(
    position: LatLng,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val target = position.toGms()
    val camera = rememberCameraPositionState { this.position = CameraPosition.fromLatLngZoom(target, STREET_ZOOM) }
    LaunchedEffect(target) { camera.position = CameraPosition.fromLatLngZoom(target, STREET_ZOOM) }

    MapBox(modifier, interactive, onClick, clickLabel = "Open full-screen map") {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = "Map of the car's location",
            googleMapOptionsFactory = if (interactive) ({ GoogleMapOptions() }) else liteOptions,
            uiSettings = if (interactive) interactiveUi else previewUi,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        ) {
            CarMarker(target)
        }
    }
}

/** A trip's GPS trace as a polyline, framed to fit, with start dot and destination flag. */
@Composable
fun TripRouteMap(
    route: List<LatLng>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (route.isEmpty()) return
    val points = remember(route) { route.map { it.toGms() } }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.last(), STREET_ZOOM - 2)
    }
    val paddingPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(points, loaded) { if (loaded) camera.frame(points, paddingPx) }

    val lineColor = MaterialTheme.colorScheme.primary
    MapBox(modifier, interactive, onClick, clickLabel = "Open full-screen route map") {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = "Map of the trip route",
            googleMapOptionsFactory = if (interactive) ({ GoogleMapOptions() }) else liteOptions,
            uiSettings = if (interactive) interactiveUi else previewUi,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapLoaded = { loaded = true },
        ) {
            if (points.size >= 2) {
                Polyline(
                    points = points,
                    color = lineColor,
                    width = with(LocalDensity.current) { 5.dp.toPx() },
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                )
            }
            StartMarker(points.first())
            DestinationMarker(points.last())
        }
    }
}

/**
 * Fits the camera to [points]. A trip whose points all coincide (PSACC records a parked car's
 * repeated positions) would zoom to the max, so degenerate bounds fall back to a street-level zoom.
 */
private suspend fun CameraPositionState.frame(points: List<GmsLatLng>, paddingPx: Int) {
    val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
    val degenerate = kotlin.math.abs(bounds.northeast.latitude - bounds.southwest.latitude) < 1e-4 &&
        kotlin.math.abs(bounds.northeast.longitude - bounds.southwest.longitude) < 1e-4
    val update = if (degenerate) {
        CameraUpdateFactory.newLatLngZoom(bounds.center, STREET_ZOOM)
    } else {
        CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
    }
    runCatching { move(update) }
}

@Composable
private fun MapBox(
    modifier: Modifier,
    interactive: Boolean,
    onClick: (() -> Unit)?,
    clickLabel: String,
    map: @Composable () -> Unit,
) {
    Box(modifier) {
        map()
        if (!interactive && onClick != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarMarker(position: GmsLatLng) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    MarkerComposable(container.toArgb(), state = rememberUpdatedMarkerState(position), title = "Car") {
        ShapedIcon(
            icon = Icons.Filled.DirectionsCar,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = container,
            contentColor = content,
            size = 44.dp,
        )
    }
}

@Composable
private fun StartMarker(position: GmsLatLng) {
    val color = MaterialTheme.colorScheme.tertiary
    val ring = MaterialTheme.colorScheme.surface
    MarkerComposable(color.toArgb(), state = rememberUpdatedMarkerState(position), title = "Start", anchor = Offset(0.5f, 0.5f)) {
        Box(
            Modifier
                .size(18.dp)
                .background(ring, CircleShape)
                .padding(3.dp)
                .background(color, CircleShape),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DestinationMarker(position: GmsLatLng) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary
    MarkerComposable(container.toArgb(), state = rememberUpdatedMarkerState(position), title = "Destination") {
        ShapedIcon(
            icon = Icons.Filled.Flag,
            shape = MaterialShapes.Pentagon.toShape(),
            containerColor = container,
            contentColor = content,
            size = 36.dp,
        )
    }
}

/** Edge-to-edge interactive map with a close button and an "open in Maps app" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMapDialog(
    title: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close map") }
                    },
                    actions = {
                        IconButton(onClick = onOpenExternal) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Maps app")
                        }
                    },
                )
            },
        ) { padding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}
