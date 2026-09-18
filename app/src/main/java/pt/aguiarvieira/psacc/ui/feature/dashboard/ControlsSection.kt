package pt.aguiarvieira.psacc.ui.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.psacc.domain.model.CarCommand
import pt.aguiarvieira.psacc.domain.model.DoorLockState
import pt.aguiarvieira.psacc.domain.model.VehicleStatus
import pt.aguiarvieira.psacc.ui.components.ShapedIcon

/** Which disruptive commands need a confirmation; kept as a saveable key rather than the object. */
private enum class Confirm(val title: String, val body: String, val confirm: String, val command: CarCommand) {
    UNLOCK("Unlock the car?", "The doors will be unlocked remotely.", "Unlock", CarCommand.Lock(false)),
    HORN("Honk the horn?", "The car will sound its horn.", "Honk", CarCommand.Horn),
    LIGHTS("Flash the lights?", "The car will flash its lights for about 10 seconds.", "Flash", CarCommand.Lights),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ControlsSection(
    status: VehicleStatus,
    pending: Set<CarCommand>,
    onCommand: (CarCommand) -> Unit,
    modifier: Modifier = Modifier,
    unavailable: Set<CarCommand> = emptySet(),
) {
    // PSA refused these for this car (no service key): shown, but disabled and labelled.
    fun refused(command: CarCommand) = unavailable.any { it::class == command::class }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }

    val lockRefused = refused(CarCommand.Lock(true))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Connected button group: the two lock states read as a single segmented control.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val lockPending = pending.any { it is CarCommand.Lock }
            ToggleButton(
                checked = status.doorLock == DoorLockState.Locked,
                onCheckedChange = { onCommand(CarCommand.Lock(true)) },
                enabled = !lockPending && !lockRefused,
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                LockButtonContent(Icons.Filled.Lock, "Lock", loading = CarCommand.Lock(true) in pending)
            }
            ToggleButton(
                checked = status.doorLock == DoorLockState.Unlocked || status.doorLock == DoorLockState.Partial,
                onCheckedChange = { confirm = Confirm.UNLOCK },
                enabled = !lockPending && !lockRefused,
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                LockButtonContent(
                    Icons.Filled.LockOpen,
                    if (status.doorLock == DoorLockState.Partial) "Partly unlocked" else "Unlock",
                    loading = CarCommand.Lock(false) in pending,
                )
            }
        }

        if (lockRefused) {
            Text(
                text = "Your car doesn't offer remote locking through PSA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val climateOn = status.preconditioning?.active == true
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                icon = Icons.Filled.AcUnit,
                label = "Climate",
                supporting = if (climateOn) "On" else "Off",
                active = climateOn,
                unavailable = refused(CarCommand.Preconditioning(true)),
                loading = pending.any { it is CarCommand.Preconditioning },
                shape = MaterialShapes.Cookie9Sided.toShape(),
                onClick = { onCommand(CarCommand.Preconditioning(!climateOn)) },
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                icon = Icons.Filled.Sync,
                label = "Update",
                supporting = "Wake the car",
                unavailable = refused(CarCommand.WakeUp),
                loading = CarCommand.WakeUp in pending,
                shape = MaterialShapes.Clover4Leaf.toShape(),
                onClick = { onCommand(CarCommand.WakeUp) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                icon = Icons.Filled.Campaign,
                label = "Horn",
                supporting = "Honk once",
                unavailable = refused(CarCommand.Horn),
                loading = CarCommand.Horn in pending,
                shape = MaterialShapes.Pentagon.toShape(),
                onClick = { confirm = Confirm.HORN },
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                icon = Icons.Filled.Highlight,
                label = "Lights",
                supporting = "Flash ~10 s",
                unavailable = refused(CarCommand.Lights),
                loading = CarCommand.Lights in pending,
                shape = MaterialShapes.Sunny.toShape(),
                onClick = { confirm = Confirm.LIGHTS },
                modifier = Modifier.weight(1f),
            )
        }
    }

    confirm?.let { c ->
        ConfirmDialog(
            title = c.title,
            body = c.body,
            confirmLabel = c.confirm,
            onConfirm = {
                confirm = null
                onCommand(c.command)
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LockButtonContent(icon: ImageVector, label: String, loading: Boolean) {
    if (loading) {
        LoadingIndicator(Modifier.size(24.dp))
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
    }
    Text(label, modifier = Modifier.padding(start = ToggleButtonDefaults.IconSpacing))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    supporting: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    loading: Boolean = false,
    unavailable: Boolean = false,
) {
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tileContainer",
    )
    Card(
        onClick = onClick,
        enabled = !loading && !unavailable,
        modifier = modifier.semantics { stateDescription = if (unavailable) NOT_AVAILABLE else supporting },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    LoadingIndicator(Modifier.size(44.dp))
                } else {
                    ShapedIcon(
                        icon = icon,
                        shape = shape,
                        size = 44.dp,
                        containerColor = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (active) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (unavailable) NOT_AVAILABLE else supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val NOT_AVAILABLE = "Not available for this car"

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
