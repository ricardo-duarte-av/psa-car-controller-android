package pt.aguiarvieira.psacc.ui.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.ErrorState
import pt.aguiarvieira.psacc.ui.components.FullScreenLoading
import pt.aguiarvieira.psacc.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        viewModel.startAutoRefresh()
        onPauseOrDispose { viewModel.stopAutoRefresh() }
    }

    when (val status = state.status) {
        ContentState.Loading -> FullScreenLoading()
        is ContentState.Error -> ErrorState(status.message, onRetry = viewModel::retry)
        is ContentState.Data -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val s = status.value
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.pictures.isNotEmpty()) {
                        item(key = "gallery") { CarGallery(pictures = state.pictures) }
                    }
                    item(key = "hero") {
                        HeroCard(status = s, lengthUnit = state.settings.lengthUnit, live = state.live)
                    }
                    item(key = "controls-header") { SectionHeader("Controls") }
                    item(key = "controls") {
                        ControlsSection(
                            status = s,
                            pending = state.pendingCommands,
                            unavailable = state.refusedCommands,
                            onCommand = viewModel::send,
                        )
                    }
                    s.electric?.let { electric ->
                        item(key = "charging-header") { SectionHeader("Charging") }
                        item(key = "charging") {
                            ChargingSection(
                                electric = electric,
                                chargeControl = state.chargeControl,
                                saving = state.savingChargeSettings,
                                pending = state.pendingCommands,
                                onCommand = viewModel::send,
                                onThresholdChange = viewModel::setChargeThreshold,
                                onStopAtChange = viewModel::setChargeStop,
                                onScheduledStartChange = viewModel::setScheduledChargeStart,
                            )
                        }
                    }
                    item(key = "status-header") { SectionHeader("Status") }
                    item(key = "status") {
                        StatusGrid(
                            status = s,
                            batterySoh = state.batterySoh,
                            maintenance = state.maintenance,
                            lengthUnit = state.settings.lengthUnit,
                        )
                    }
                    s.position?.let { position ->
                        item(key = "location-header") { SectionHeader("Location") }
                        item(key = "location") { LocationCard(position) }
                    }
                }
            }
        }
    }
}
