package pt.aguiarvieira.psacc.ui.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import pt.aguiarvieira.psacc.notifications.LastCheck
import pt.aguiarvieira.psacc.notifications.NotificationCategory
import pt.aguiarvieira.psacc.notifications.NotificationSettings
import java.time.Instant
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.aguiarvieira.psacc.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("Server")
                state.config?.let { config ->
                    SettingsRow(Icons.Filled.Dns, "Address", config.baseUrl)
                    SettingsRow(Icons.Filled.Person, "User", config.username.ifEmpty { "No login" })
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = "Open web interface",
                        subtitle = "Sign in to PSA and change server settings in your browser",
                        onClick = { uriHandler.openUri(config.baseUrl) },
                    )
                }
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Disconnect",
                    subtitle = "Forget this server and its password",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { confirmDisconnect = true },
                )

                if (state.vehicles.isNotEmpty()) {
                    HorizontalDivider()
                    SectionTitle("Vehicles")
                    state.vehicles.forEach { v ->
                        SettingsRow(
                            icon = Icons.Filled.DirectionsCar,
                            title = listOfNotNull(v.brand, v.displayName).joinToString(" "),
                            subtitle = buildList {
                                add(v.vin)
                                v.batteryKwh?.takeIf { it > 0 }?.let { add("${Formatters.number(it, 1)} kWh battery") }
                                v.fuelCapacityLitres?.takeIf { it > 0 }?.let { add("${it.toInt()} L tank") }
                            }.joinToString(" · "),
                        )
                    }
                }

                HorizontalDivider()
                SectionTitle("Server configuration")
                val s = state.serverSettings
                SettingsRow(Icons.Filled.Straighten, "Distance unit", s.lengthUnit)
                SettingsRow(
                    icon = Icons.Filled.AttachMoney,
                    title = "Electricity price",
                    subtitle = buildList {
                        add("Day ${s.dayPrice?.let { Formatters.money(it, s.currency) } ?: "not set"}")
                        s.nightPrice?.let { add("night ${Formatters.money(it, s.currency)}") }
                        if (s.nightStart != null && s.nightEnd != null) add("${s.nightStart}–${s.nightEnd}")
                    }.joinToString(" · "),
                )
                Text(
                    text = "Edit these in PSA Car Controller's web interface.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()
                NotificationsSection(
                    settings = state.notifications,
                    lastCheck = state.lastCheck,
                    canPost = viewModel::canPostNotifications,
                    onEnabledChange = viewModel::setNotificationsEnabled,
                    onIntervalChange = viewModel::setInterval,
                    onCategoryChange = viewModel::setCategoryEnabled,
                    onCheckNow = viewModel::checkNow,
                )

                HorizontalDivider()
                // Changelog and About are always the last two entries (changelog first).
                SettingsRow(Icons.Filled.History, "Changelog", "What's new in each version", onClick = onOpenChangelog, chevron = true)
                SettingsRow(Icons.Filled.Info, "About", "Version and source code", onClick = onOpenAbout, chevron = true)
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Disconnect?") },
            text = { Text("The server address and password will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsSection(
    settings: NotificationSettings,
    lastCheck: LastCheck?,
    canPost: () -> Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onCategoryChange: (NotificationCategory, Boolean) -> Unit,
    onCheckNow: () -> Unit,
) {
    val context = LocalContext.current
    // Re-checked on every resume, e.g. when returning from the system notification settings.
    var canPostNow by remember { mutableStateOf(canPost()) }
    LifecycleResumeEffect(Unit) {
        canPostNow = canPost()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPostNow = canPost()
        if (granted) {
            onEnabledChange(true)
        } else {
            Toast.makeText(context, "Notifications permission denied", Toast.LENGTH_LONG).show()
        }
    }
    val blocked = settings.enabled && !canPostNow

    SectionTitle("Notifications")
    SwitchRow(
        title = "Background checks",
        subtitle = "Check the server periodically and notify about new trips, ignition and charging",
        checked = settings.enabled,
        onCheckedChange = { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onEnabledChange(enabled)
            }
        },
    )

    if (blocked) {
        SettingsRow(
            icon = Icons.Filled.NotificationsOff,
            title = "Notifications are blocked",
            subtitle = "Tap to allow them in system settings",
            tint = MaterialTheme.colorScheme.error,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
    }

    if (settings.enabled) {
        Text("Check every", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationSettings.INTERVAL_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = settings.intervalMinutes == minutes,
                    onClick = { onIntervalChange(minutes) },
                    label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} h") },
                )
            }
        }
        Text(
            text = "Android may delay checks to save battery, so notifications can arrive later than " +
                "this, and short changes between checks (a quick drive) may be missed. Checks read " +
                "PSA Car Controller's saved data and don't use your PSA API quota.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NotificationCategory.entries.forEach { category ->
            SwitchRow(
                title = category.title,
                subtitle = category.description,
                checked = settings.allows(category),
                onCheckedChange = { onCategoryChange(category, it) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Last check", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when {
                        lastCheck == null -> "Not yet"
                        lastCheck.error != null ->
                            "${Formatters.relative(Instant.ofEpochMilli(lastCheck.atMillis))} · ${lastCheck.error}"
                        else -> Formatters.relative(Instant.ofEpochMilli(lastCheck.atMillis))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastCheck?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCheckNow) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Check now", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}
