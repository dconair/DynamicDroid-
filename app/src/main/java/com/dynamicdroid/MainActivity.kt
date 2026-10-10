package com.dynamicdroid

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynamicdroid.liveupdate.LiveUpdateCapabilities
import com.dynamicdroid.service.NavigationService
import com.dynamicdroid.state.NavigationUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DynamicDroidApp
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { HomeScreen(app) }
            }
        }
    }
}

private data class PermissionStatus(
    val location: Boolean,
    val notifications: Boolean,
    val liveUpdates: Boolean,
    val liveUpdatesSupported: Boolean,
    val overlay: Boolean,
    val batteryUnrestricted: Boolean,
)

@Composable
private fun HomeScreen(app: DynamicDroidApp) {
    val context = LocalContext.current
    val navState by app.navigationState.state.collectAsStateWithLifecycle()

    // Berechtigungen werden bei jedem Zurückkehren aus den Systemeinstellungen neu gelesen.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val status = remember(refresh) { readStatus(context) }

    var islandEnabled by remember { mutableStateOf(app.featureFlags.islandOverlayEnabled) }
    var speed by remember { mutableStateOf(5.0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        when (val s = navState) {
            NavigationUiState.Idle -> Text(stringResource(R.string.home_idle))
            is NavigationUiState.Active -> {
                val content = app.contentMapper.map(s)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(content.title, style = MaterialTheme.typography.titleMedium)
                        Text(content.text)
                        LinearProgressIndicator(
                            progress = { content.progressMeters.toFloat() / content.progressMaxMeters },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // --- Berechtigungen -------------------------------------------------------------------------------
        PermissionRow(stringResource(R.string.perm_location), status.location) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(stringResource(R.string.perm_notifications), status.notifications) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        if (status.liveUpdatesSupported) {
            PermissionRow(stringResource(R.string.perm_live_updates), status.liveUpdates) {
                context.startActivity(
                    // Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS steht nur in der Doku, nicht im SDK 36.1 –
                    // der Live-Updates-Schalter liegt auf der App-Benachrichtigungsseite.
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        } else {
            Text(stringResource(R.string.live_updates_unsupported), style = MaterialTheme.typography.bodySmall)
        }
        PermissionRow(stringResource(R.string.perm_battery), status.batteryUnrestricted) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        // --- Dynamic-Island-Overlay (Feature-Flag) -------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = islandEnabled, onCheckedChange = {
                islandEnabled = it
                app.featureFlags.islandOverlayEnabled = it
            })
            Text(stringResource(R.string.flag_island_overlay), Modifier.weight(1f))
        }
        if (islandEnabled) {
            PermissionRow(stringResource(R.string.perm_overlay), status.overlay) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            }
        }

        // --- Steuerung -----------------------------------------------------------------------------------
        Text(stringResource(R.string.speed_label), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.0, 5.0, 20.0).forEach { factor ->
                FilterChip(selected = speed == factor, onClick = { speed = factor }, label = { Text("×${factor.toInt()}") })
            }
        }

        val running = navState is NavigationUiState.Active
        if (!status.location && !running) {
            Text(stringResource(R.string.start_needs_location), color = MaterialTheme.colorScheme.error)
        } else if (!status.notifications && !running) {
            Text(stringResource(R.string.start_without_notifications), color = MaterialTheme.colorScheme.error)
        }
        if (running) {
            Button(onClick = { NavigationService.stop(context) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_stop_navigation))
            }
        } else {
            Button(
                onClick = { NavigationService.start(context, NavigationService.SOURCE_MOCK, speed) },
                enabled = status.location,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.start_mock)) }
            OutlinedButton(
                onClick = { NavigationService.start(context, NavigationService.SOURCE_GPS, 1.0) },
                enabled = status.location,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.start_gps)) }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${if (granted) "✓" else "✗"}  $label", Modifier.weight(1f))
        if (!granted) OutlinedButton(onClick = onFix) { Text(stringResource(R.string.fix)) }
    }
}

private fun readStatus(context: android.content.Context): PermissionStatus {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val caps = LiveUpdateCapabilities(context)
    val nm = context.getSystemService(NotificationManager::class.java)
    return PermissionStatus(
        location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS) && nm.areNotificationsEnabled()
        } else nm.areNotificationsEnabled(),
        liveUpdates = caps.canPostPromoted(),
        liveUpdatesSupported = caps.supportsProgressStyle,
        overlay = Settings.canDrawOverlays(context),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    )
}
