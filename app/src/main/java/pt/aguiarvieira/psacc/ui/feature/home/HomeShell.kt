package pt.aguiarvieira.psacc.ui.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.psacc.BuildConfig
import pt.aguiarvieira.psacc.domain.model.Vehicle
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.ErrorState
import pt.aguiarvieira.psacc.ui.components.FullScreenLoading
import pt.aguiarvieira.psacc.ui.feature.charging.ChargingScreen
import pt.aguiarvieira.psacc.ui.feature.dashboard.DashboardScreen
import pt.aguiarvieira.psacc.ui.feature.trips.TripsScreen

enum class HomeTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    CAR("Car", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
    TRIPS("Trips", Icons.Filled.Route, Icons.Outlined.Route),
    CHARGING("Charging", Icons.Filled.ElectricBolt, Icons.Outlined.ElectricBolt),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeShell(
    onOpenSettings: () -> Unit,
    requestedTab: String? = null,
    onRequestedTabShown: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CAR) }
    // Tab names match MainActivity.TAB_* (the extras notifications carry).
    LaunchedEffect(requestedTab) {
        requestedTab?.let { name ->
            HomeTab.entries.firstOrNull { it.name == name }?.let { selectedTab = it }
            onRequestedTabShown()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            HomeTab.entries.forEach { tab ->
                item(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    icon = {
                        Icon(if (tab == selectedTab) tab.selectedIcon else tab.icon, contentDescription = null)
                    },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        VehicleTitle(
                            vehicles = (state.vehicles as? ContentState.Data)?.value.orEmpty(),
                            selected = state.selected,
                            onSelect = viewModel::select,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                banner?.let {
                    UpdateBannerCard(
                        banner = it,
                        onRestart = viewModel::completeUpdate,
                        onUpdate = viewModel::startUpdate,
                        onDismiss = viewModel::dismissServerNotice,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val vehicles = state.vehicles) {
                        ContentState.Loading -> FullScreenLoading()
                        is ContentState.Error -> ErrorState(vehicles.message, onRetry = viewModel::load)
                        is ContentState.Data -> {
                            val vehicle = state.selected
                            if (vehicle == null) {
                                FullScreenLoading()
                            } else {
                                val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                                val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                                AnimatedContent(
                                    targetState = selectedTab,
                                    transitionSpec = {
                                        fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
                                    },
                                    label = "homeTabs",
                                ) { tab ->
                                    when (tab) {
                                        HomeTab.CAR -> DashboardScreen(snackbarHostState = snackbarHostState)
                                        HomeTab.TRIPS -> TripsScreen(
                                            isPrimaryVehicle = vehicles.value.firstOrNull()?.vin == vehicle.vin,
                                        )
                                        HomeTab.CHARGING -> ChargingScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A newer release of the app, or of the server: they release in step. */
@Composable
private fun UpdateBannerCard(
    banner: UpdateBanner,
    onRestart: () -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val app = BuildConfig.VERSION_NAME
    val text = when (banner) {
        UpdateBanner.Downloaded -> "A new version of the app is ready."
        is UpdateBanner.AppBehind -> {
            val server = banner.serverVersion ?: "a newer release"
            "Your server runs $server, this app $app. " +
                if (banner.canUpdate) "Update the app to match it." else "Update the app from where you installed it."
        }
        is UpdateBanner.ServerBehind -> {
            val server = banner.serverVersion?.let { "runs $it" } ?: "runs an older release"
            "Your server $server. Update it to $app for everything in this version of the app."
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            when (banner) {
                UpdateBanner.Downloaded -> TextButton(onClick = onRestart) { Text("Restart") }
                is UpdateBanner.AppBehind -> if (banner.canUpdate) TextButton(onClick = onUpdate) { Text("Update") }
                is UpdateBanner.ServerBehind -> IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VehicleTitle(
    vehicles: List<Vehicle>,
    selected: Vehicle?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Column {
            Text(
                text = selected?.displayName ?: "PSA Car Controller",
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected?.brand != null) {
                Text(
                    text = selected.brand,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (vehicles.size <= 1) {
        content()
        return
    }
    Box {
        TextButton(onClick = { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                content()
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose vehicle")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(vehicle.displayName)
                            Text(
                                vehicle.vin,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(vehicle.vin)
                    },
                )
            }
        }
    }
}
