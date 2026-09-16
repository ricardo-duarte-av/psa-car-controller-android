package pt.aguiarvieira.psacc.ui.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.ChargeControlSettings
import pt.aguiarvieira.psacc.domain.model.ChargeStatus
import pt.aguiarvieira.psacc.domain.model.ElectricEnergy
import pt.aguiarvieira.psacc.domain.model.HourMinute
import pt.aguiarvieira.psacc.util.Formatters
import kotlin.math.roundToInt

private enum class TimeDialog { STOP_AT, SCHEDULED_START }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChargingSection(
    electric: ElectricEnergy,
    chargeControl: ChargeControlSettings?,
    saving: Boolean,
    pending: Set<CarCommand>,
    onCommand: (CarCommand) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onStopAtChange: (HourMinute?) -> Unit,
    onScheduledStartChange: (HourMinute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val charging = electric.charging
    var confirmCharge by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var timeDialog by rememberSaveable { mutableStateOf<TimeDialog?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (charging != null) {
                InfoRow(Icons.Filled.EvStation, "Status", chargeStatusText(charging.status, charging.rawStatus, charging.plugged))
                charging.mode?.takeIf { it != "No" }?.let { InfoRow(Icons.Filled.Speed, "Mode", it) }
                if (charging.status == ChargeStatus.InProgress) {
                    charging.rateKmh?.takeIf { it > 0 }?.let {
                        InfoRow(Icons.Filled.BatteryChargingFull, "Rate", "${it.roundToInt()} km/h")
                    }
                    charging.remaining?.let { InfoRow(Icons.Filled.Schedule, "Time left", Formatters.duration(it)) }
                }

                val isCharging = charging.status == ChargeStatus.InProgress
                val chargePending = pending.any { it is CarCommand.Charge }
                val action: @Composable () -> Unit = {
                    if (chargePending) {
                        LoadingIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(
                            if (isCharging) Icons.Filled.Stop else Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(if (isCharging) "Stop charging" else "Charge now")
                }
                if (isCharging) {
                    FilledTonalButton(
                        onClick = { confirmCharge = false },
                        enabled = !chargePending,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) { action() }
                } else {
                    Button(
                        onClick = { confirmCharge = true },
                        enabled = !chargePending && charging.plugged,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) { action() }
                }
            }

            HorizontalDivider()

            ClickableRow(
                icon = Icons.Filled.Schedule,
                label = "Scheduled charge start",
                value = Formatters.timeOfDay(charging?.scheduledStart) ?: "Not set",
                enabled = !saving,
                onClick = { timeDialog = TimeDialog.SCHEDULED_START },
            )

            if (chargeControl != null) {
                ThresholdSlider(
                    value = chargeControl.thresholdPercent,
                    enabled = !saving,
                    onValueChangeFinished = onThresholdChange,
                )
                ClickableRow(
                    icon = Icons.Filled.AlarmOff,
                    label = "Stop charging at",
                    value = chargeControl.stopAt?.toString() ?: "Off",
                    enabled = !saving,
                    onClick = { timeDialog = TimeDialog.STOP_AT },
                )
            } else {
                Text(
                    text = "Charge limit and stop time need charge control enabled in PSA Car Controller.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirmCharge?.let { start ->
        ConfirmDialog(
            title = if (start) "Start charging now?" else "Stop charging?",
            body = if (start) {
                "The car will start charging immediately, ignoring its scheduled start time."
            } else {
                "The charge will stop and resume at the scheduled start time."
            },
            confirmLabel = if (start) "Charge" else "Stop",
            onConfirm = {
                confirmCharge = null
                onCommand(CarCommand.Charge(start))
            },
            onDismiss = { confirmCharge = null },
        )
    }

    when (timeDialog) {
        TimeDialog.SCHEDULED_START -> {
            val current = charging?.scheduledStart
            TimeDialog(
                title = "Start charging at",
                initial = HourMinute((current?.toHours() ?: 22).toInt() % 24, current?.toMinutesPart() ?: 0),
                onConfirm = {
                    timeDialog = null
                    onScheduledStartChange(it)
                },
                onDismiss = { timeDialog = null },
            )
        }
        TimeDialog.STOP_AT -> TimeDialog(
            title = "Stop charging at",
            initial = chargeControl?.stopAt ?: HourMinute(7, 0),
            onConfirm = {
                timeDialog = null
                onStopAtChange(it)
            },
            onDismiss = { timeDialog = null },
            onClear = if (chargeControl?.stopAt != null) {
                {
                    timeDialog = null
                    onStopAtChange(null)
                }
            } else {
                null
            },
        )
        null -> Unit
    }
}

private fun chargeStatusText(status: ChargeStatus, raw: String?, plugged: Boolean): String = when (status) {
    ChargeStatus.InProgress -> "Charging"
    ChargeStatus.Finished -> "Finished"
    ChargeStatus.Stopped -> if (plugged) "Plugged in, waiting" else "Stopped"
    ChargeStatus.Disconnected -> "Not plugged in"
    ChargeStatus.Failure -> "Failed"
    ChargeStatus.Unknown -> raw ?: "Unknown"
}

@Composable
private fun ThresholdSlider(value: Int, enabled: Boolean, onValueChangeFinished: (Int) -> Unit) {
    // Local draft for smooth dragging; only the final value is sent to PSACC.
    var draft by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { draft = value.toFloat() }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Charge limit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = if (draft.roundToInt() >= 100) "No limit" else "${draft.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val v = draft.roundToInt()
                if (v != value) onValueChangeFinished(v)
            },
            valueRange = 50f..100f,
            steps = 9,
            enabled = enabled,
        )
        Text(
            text = "PSA Car Controller stops the charge once the battery reaches this level.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp).weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ClickableRow(icon: ImageVector, label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp).weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    title: String,
    initial: HourMinute,
    onConfirm: (HourMinute) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24h)
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
                TimePicker(state = pickerState)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onClear != null) TextButton(onClick = onClear) { Text("Turn off") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(HourMinute(pickerState.hour, pickerState.minute)) }) { Text("Save") }
                }
            }
        }
    }
}
