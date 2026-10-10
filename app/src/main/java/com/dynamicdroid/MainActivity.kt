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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { HomeScreen(app) }
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

private val CardShape = RoundedCornerShape(28.dp)
private val SoftSpring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

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

    // Zuletzt aktiver Inhalt bleibt während der Ausblend-Animation stehen, statt sofort zu verschwinden.
    val active = navState as? NavigationUiState.Active
    var lastContent by remember { mutableStateOf<com.dynamicdroid.liveupdate.LiveUpdateContent?>(null) }
    if (active != null) lastContent = app.contentMapper.map(active)
    val running = active != null

    Column(
        Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)

        AnimatedVisibility(
            visible = running,
            enter = fadeIn(tween(300)) + expandVertically(SoftSpring),
            exit = fadeOut(tween(200)) + shrinkVertically(SoftSpring),
        ) {
            lastContent?.let { ActiveRouteCard(it) }
        }
        AnimatedVisibility(
            visible = !running,
            enter = fadeIn(tween(300, delayMillis = 150)) + expandVertically(SoftSpring),
            exit = fadeOut(tween(120)) + shrinkVertically(SoftSpring),
        ) {
            Text(stringResource(R.string.home_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Steuerung -----------------------------------------------------------------------------------
        SectionCard {
            Text(stringResource(R.string.speed_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0, 5.0, 20.0).forEach { factor ->
                    FilterChip(selected = speed == factor, onClick = { speed = factor }, label = { Text("×${factor.toInt()}") }, enabled = !running)
                }
            }
            AnimatedVisibility(visible = !status.location && !running) {
                Text(stringResource(R.string.start_needs_location), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            AnimatedVisibility(visible = status.location && !status.notifications && !running) {
                Text(stringResource(R.string.start_without_notifications), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (running) {
                Button(onClick = { NavigationService.stop(context) }, Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                    Text(stringResource(R.string.action_stop_navigation))
                }
            } else {
                Button(
                    onClick = { NavigationService.start(context, NavigationService.SOURCE_MOCK, speed) },
                    enabled = status.location,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                ) { Text(stringResource(R.string.start_mock)) }
                OutlinedButton(
                    onClick = { NavigationService.start(context, NavigationService.SOURCE_GPS, 1.0) },
                    enabled = status.location,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                ) { Text(stringResource(R.string.start_gps)) }
            }
        }

        // --- Berechtigungen -------------------------------------------------------------------------------
        SectionCard {
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
                Text(stringResource(R.string.live_updates_unsupported), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PermissionRow(stringResource(R.string.perm_battery), status.batteryUnrestricted) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        // --- Dynamic-Island-Overlay (Feature-Flag) -------------------------------------------------------
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.flag_island_overlay), Modifier.weight(1f))
                Switch(checked = islandEnabled, onCheckedChange = {
                    islandEnabled = it
                    app.featureFlags.islandOverlayEnabled = it
                })
            }
            AnimatedVisibility(visible = islandEnabled) {
                PermissionRow(stringResource(R.string.perm_overlay), status.overlay) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(SoftSpring),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun ActiveRouteCard(content: com.dynamicdroid.liveupdate.LiveUpdateContent) {
    val progress by animateFloatAsState(
        (content.progressMeters.toFloat() / content.progressMaxMeters).coerceIn(0f, 1f),
        tween(1_000, easing = LinearEasing),
        label = "routeProgress",
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(content.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(content.text, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            ),
        )
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) OutlinedButton(onClick = onFix, shape = CircleShape) { Text(stringResource(R.string.fix)) }
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
