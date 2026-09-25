package pt.aguiarvieira.psacc.ui.feature.charging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.psacc.domain.model.ChargePlace
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.ui.components.ContentState
import pt.aguiarvieira.psacc.ui.components.ErrorState
import pt.aguiarvieira.psacc.ui.components.FullScreenLoading
import pt.aguiarvieira.psacc.ui.components.MessageState
import pt.aguiarvieira.psacc.ui.components.ShapedIcon
import pt.aguiarvieira.psacc.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingScreen(viewModel: ChargingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val sessions = state.sessions) {
        ContentState.Loading -> FullScreenLoading()
        is ContentState.Error -> ErrorState(sessions.message, onRetry = viewModel::retry)
        is ContentState.Data -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (sessions.value.isEmpty()) {
                MessageState(
                    icon = Icons.Filled.EvStation,
                    title = "No charging sessions yet",
                    body = "Sessions show up here once PSA Car Controller has recorded a charge.",
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
                        state.summary?.let { item(key = "summary") { SummaryCard(it, state.settings) } }
                        itemsIndexed(sessions.value, key = { index, s -> "$index-${s.startAt}" }) { _, session ->
                            SessionCard(session, state.settings, onEdit = { viewModel.startEdit(session) })
                        }
                    }
                }
            }
        }
    }

    state.edit?.let { edit ->
        SessionEditSheet(edit, state.settings, onDismiss = viewModel::dismissEdit, onSave = viewModel::saveEdit)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryCard(summary: ChargingSummary, settings: ServerSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("${Formatters.number(summary.totalKwh, 1)} kWh", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                "added over ${summary.count} ${if (summary.count == 1) "session" else "sessions"}",
                style = MaterialTheme.typography.titleMedium,
            )
            summary.totalCost?.let {
                Text(
                    "${Formatters.money(it, settings.currency)} total",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionCard(session: ChargingSession, settings: ServerSettings, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        enabled = session.editable,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        // enabled only decides whether it opens the editor: a session in progress looks the same
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    icon = Icons.Filled.ElectricBolt,
                    shape = if (session.inProgress) MaterialShapes.SoftBurst.toShape() else MaterialShapes.Cookie4Sided.toShape(),
                    containerColor = if (session.inProgress) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (session.inProgress) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Formatters.dateTime(session.startAt), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (session.inProgress) "In progress" else "${Formatters.duration(session.duration)} · until ${Formatters.time(session.stopAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.place != ChargePlace.Home) {
                    SuggestionChip(onClick = onEdit, label = { Text(session.place.label) })
                }
                session.mode?.takeIf { it.isNotBlank() && it != "No" }?.let { mode ->
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(onClick = {}, label = { Text(mode) })
                }
            }

            LevelBar(start = session.startLevel, end = session.endLevel)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                session.energy?.let {
                    Metric("${Formatters.number(it, 1)} kWh", if (session.meteredKwh != null) "billed" else "energy")
                }
                session.price?.let {
                    Metric(Formatters.money(it, settings.currency), if (session.priceManual) "paid" else "est. cost")
                }
                session.co2?.takeIf { it > 0 }?.let { Metric("${it.toInt()} g", "CO₂/kWh") }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A battery-shaped bar highlighting the segment between the start and end charge levels. */
@Composable
private fun LevelBar(start: Double?, end: Double?) {
    val from = ((start ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
    val to = ((end ?: start ?: 0.0) / 100).toFloat().coerceIn(from, 1f)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            contentDescription = "Charged from ${Formatters.percent(start)} to ${Formatters.percent(end)}"
        },
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * from)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Box(
                Modifier
                    .offset(x = maxWidth * from)
                    .fillMaxHeight()
                    .width(maxWidth * (to - from))
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Formatters.percent(start), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(Formatters.percent(end), style = MaterialTheme.typography.labelMedium)
        }
    }
}
