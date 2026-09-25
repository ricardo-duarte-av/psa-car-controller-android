package pt.aguiarvieira.psacc.ui.feature.charging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.aguiarvieira.psacc.domain.model.ChargePlace
import pt.aguiarvieira.psacc.domain.model.ChargingSession
import pt.aguiarvieira.psacc.domain.model.ChargingSessionEdit
import pt.aguiarvieira.psacc.domain.model.ServerSettings
import pt.aguiarvieira.psacc.util.Formatters
import java.util.Locale

/**
 * Sets by hand what PSACC can't know about a session: where it charged, the kWh the charger billed and
 * what it cost. A blank field goes back to PSACC's estimate.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SessionEditSheet(
    state: SessionEditState,
    settings: ServerSettings,
    onDismiss: () -> Unit,
    onSave: (ChargingSessionEdit) -> Unit,
) {
    val session = state.session
    var place by rememberSaveable { mutableStateOf(session.place) }
    var energyText by rememberSaveable { mutableStateOf(session.meteredKwh?.let(::amountText).orEmpty()) }
    var priceText by rememberSaveable {
        mutableStateOf(session.price?.takeIf { session.priceManual }?.let(::amountText).orEmpty())
    }
    val energy = parseAmount(energyText)
    val price = parseAmount(priceText)
    val valid = energy?.isNaN() != true && price?.isNaN() != true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Edit session", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${Formatters.dateTime(session.startAt)} · ${Formatters.duration(session.duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ChargePlace.entries.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = place == option,
                        onCheckedChange = { place = option },
                        enabled = !state.saving,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            ChargePlace.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(option.label)
                    }
                }
            }

            AmountField(
                text = energyText,
                onTextChange = { energyText = it },
                label = "Energy billed (kWh)",
                hint = session.kwh?.let { "Leave empty for the estimate of ${Formatters.number(it, 1)} kWh" }
                    ?: "Leave empty for the estimate",
                invalid = energy?.isNaN() == true,
                enabled = !state.saving,
            )
            AmountField(
                text = priceText,
                onTextChange = { priceText = it },
                label = "Cost (${settings.currency})",
                hint = if (place == ChargePlace.Work) {
                    "Leave empty: charging at work is free"
                } else {
                    "Leave empty to estimate it from the energy price"
                },
                invalid = price?.isNaN() == true,
                enabled = !state.saving,
            )

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.saving) { Text("Cancel") }
                Button(
                    onClick = { onSave(ChargingSessionEdit(place, energy, price)) },
                    enabled = valid && !state.saving,
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    hint: String,
    invalid: Boolean,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        supportingText = { Text(if (invalid) "Enter a positive number" else hint) },
        isError = invalid,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What a field holds: null when blank, NaN when it isn't a positive number. A comma is a decimal point. */
internal fun parseAmount(text: String): Double? {
    val trimmed = text.trim().takeIf { it.isNotEmpty() } ?: return null
    return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() } ?: Double.NaN
}

private fun amountText(value: Double): String = "%.2f".format(Locale.US, value)

/** Only a finished session can be edited: its energy and cost are known by then. */
internal val ChargingSession.editable: Boolean get() = !inProgress && startAt != null
