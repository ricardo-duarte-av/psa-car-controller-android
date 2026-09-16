package pt.aguiarvieira.psacc.ui.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ElectricEnergy
import pt.aguiarvieira.psacc.domain.model.FuelEnergy
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.util.Formatters

/** The big battery ring (or fuel gauge for ICE cars), range, charge state and data freshness. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroCard(status: VehicleStatus, lengthUnit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val electric = status.electric
            if (electric != null) {
                BatteryRing(electric, lengthUnit)
                ChargeChip(electric)
            }
            status.fuel?.let { fuel ->
                if (electric != null) FuelBar(fuel, lengthUnit) else FuelOnly(fuel, lengthUnit)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = " Updated ${Formatters.relative(status.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BatteryRing(electric: ElectricEnergy, lengthUnit: String) {
    val level = ((electric.levelPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = level,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "battery",
    )
    val charging = electric.charging?.status == ChargeStatus.InProgress
    val strokeWidth = with(LocalDensity.current) { 14.dp.toPx() }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        CircularWavyProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(220.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            trackStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            // The wave only moves while charging, so a glance tells you energy is flowing in.
            amplitude = { if (charging) 1f else 0f },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Formatters.percent(electric.levelPercent),
                style = MaterialTheme.typography.displayLargeEmphasized,
            )
            Text(
                text = Formatters.distance(electric.rangeKm, lengthUnit),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = "electric range", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChargeChip(electric: ElectricEnergy) {
    val charging = electric.charging ?: return
    val (icon, text) = when {
        charging.status == ChargeStatus.InProgress -> Icons.Filled.BatteryChargingFull to buildString {
            append("Charging")
            charging.remaining?.let { append(" · ${Formatters.duration(it)} left") }
        }
        charging.status == ChargeStatus.Finished && charging.plugged -> Icons.Filled.BatteryChargingFull to "Charge complete"
        charging.status == ChargeStatus.Failure -> Icons.Filled.PowerOff to "Charging failed"
        charging.plugged -> Icons.Filled.ElectricalServices to buildString {
            append("Plugged in")
            Formatters.timeOfDay(charging.scheduledStart)?.let { append(" · starts at $it") }
        }
        else -> Icons.Filled.PowerOff to "Not plugged in"
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = null,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FuelBar(fuel: FuelEnergy, lengthUnit: String) {
    val level = ((fuel.levelPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalGasStation, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = " Fuel ${Formatters.percent(fuel.levelPercent)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(text = Formatters.distance(fuel.rangeKm, lengthUnit), style = MaterialTheme.typography.titleSmall)
        }
        LinearWavyProgressIndicator(
            progress = { level },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            amplitude = { 0f },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FuelOnly(fuel: FuelEnergy, lengthUnit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LocalGasStation, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(Formatters.percent(fuel.levelPercent), style = MaterialTheme.typography.displayLargeEmphasized)
        Text("${Formatters.distance(fuel.rangeKm, lengthUnit)} range", style = MaterialTheme.typography.titleMedium)
    }
}
