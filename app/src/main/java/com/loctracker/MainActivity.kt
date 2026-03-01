package com.loctracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.loctracker.data.db.AppDatabase
import com.loctracker.data.db.LocationEntity
import com.loctracker.service.LocationService
import com.loctracker.service.LocationService.TrackingState
import com.loctracker.ui.screens.SettingsScreen
import com.loctracker.ui.theme.LocTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocTrackerTheme {
                var currentScreen by remember { mutableStateOf("home") }
                val context = LocalContext.current

                when (currentScreen) {
                    "home" -> HomeScreen(
                        onOpenSettings = { currentScreen = "settings" }
                    )
                    "settings" -> SettingsScreen(
                        onBack = { currentScreen = "home" },
                        onSettingsChanged = {
                            // Only notify the service if it's actually tracking
                            if (LocationService.state.value != TrackingState.STOPPED) {
                                val intent = Intent(context, LocationService::class.java).apply {
                                    action = LocationService.ACTION_UPDATE_SETTINGS
                                }
                                context.startService(intent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocTracker", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LocationScreen(modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun LocationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.locationDao() }
    val scope = rememberCoroutineScope()

    val recentLocations by dao.getRecentLocations(10).collectAsState(initial = emptyList())
    val locationCount by dao.getCount().collectAsState(initial = 0)

    // Restore service state from DataStore on cold start (companion object statics
    // reset to defaults when Android kills the app process)
    LaunchedEffect(Unit) {
        LocationService.restoreStateFromDataStore(context)
    }

    // Observe service state
    val trackingState by LocationService.state.collectAsState()
    val resumeAtMillis by LocationService.resumeAtMillis.collectAsState()
    val currentIntervalMinutes by LocationService.currentIntervalMinutes.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }

    // Countdown timer for pause remaining time
    var remainingSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(trackingState, resumeAtMillis) {
        if (trackingState == TrackingState.PAUSED && resumeAtMillis > 0) {
            while (true) {
                val remaining = (resumeAtMillis - System.currentTimeMillis()) / 1000
                remainingSeconds = if (remaining > 0) remaining else 0
                if (remaining <= 0) break
                delay(1000)
            }
            // Timer expired but service may not have auto-resumed yet (no location
            // callback has fired). Nudge the service to resume immediately.
            if (trackingState == TrackingState.PAUSED) {
                val intent = Intent(context, LocationService::class.java).apply {
                    action = LocationService.ACTION_RESUME
                }
                context.startService(intent)
            }
        } else {
            remainingSeconds = 0
        }
    }

    // Permission state
    var hasForegroundPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasBackgroundPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasForegroundPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!hasForegroundPermission) {
            errorMessage = "Location permission denied. Please grant it in app settings."
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundPermission = granted
        if (!granted) {
            errorMessage = "Background location denied. Tracking may not work when the app is closed."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        // Start tracking after the permission dialog is dismissed (regardless of result,
        // since notification permission is optional — tracking works without it)
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startTracking() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopTracking() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun pauseTracking(durationMinutes: Long) {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_PAUSE
            putExtra(LocationService.EXTRA_PAUSE_DURATION_MS, durationMinutes * 60 * 1000)
        }
        context.startService(intent)
    }

    fun resumeTracking() {
        val intent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun handleStartStop() {
        if (trackingState != TrackingState.STOPPED) {
            stopTracking()
            return
        }

        errorMessage = null

        if (!hasForegroundPermission) {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (!hasBackgroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }

        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return // startTracking() will be called from the permission callback
        }

        startTracking()
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Locations") },
            text = { Text("Delete all $locationCount saved locations? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { dao.deleteAll() }
                        showClearDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pause duration picker dialog
    if (showPauseDialog) {
        PauseDurationDialog(
            onDismiss = { showPauseDialog = false },
            onSelect = { minutes ->
                pauseTracking(minutes)
                showPauseDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status card
        item {
            Spacer(modifier = Modifier.height(8.dp))

            val containerColor = when (trackingState) {
                TrackingState.TRACKING -> MaterialTheme.colorScheme.primaryContainer
                TrackingState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                TrackingState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when (trackingState) {
                TrackingState.TRACKING -> MaterialTheme.colorScheme.onPrimaryContainer
                TrackingState.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
                TrackingState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            fontSize = 14.sp,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = when (trackingState) {
                                TrackingState.TRACKING -> "Tracking"
                                TrackingState.PAUSED -> "Paused"
                                TrackingState.STOPPED -> "Stopped"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = contentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total points",
                            fontSize = 14.sp,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$locationCount",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = contentColor
                        )
                    }
                    if (trackingState == TrackingState.TRACKING) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Interval",
                                fontSize = 14.sp,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$currentIntervalMinutes min",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = contentColor
                            )
                        }
                    }
                    if (trackingState == TrackingState.PAUSED && remainingSeconds > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Resumes in",
                                fontSize = 14.sp,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatRemainingTime(remainingSeconds),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }

        // Start/Stop button
        item {
            Button(
                onClick = { handleStartStop() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = if (trackingState != TrackingState.STOPPED)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = if (trackingState != TrackingState.STOPPED) "Stop Tracking" else "Start Tracking",
                    fontSize = 16.sp
                )
            }
        }

        // Pause / Resume button
        if (trackingState == TrackingState.TRACKING) {
            item {
                OutlinedButton(
                    onClick = { showPauseDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(48.dp)
                ) {
                    Text("Pause Tracking", fontSize = 15.sp)
                }
            }
        } else if (trackingState == TrackingState.PAUSED) {
            item {
                Button(
                    onClick = { resumeTracking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(48.dp)
                ) {
                    Text("Resume Now", fontSize = 15.sp)
                }
            }
        }

        // Permission warnings
        if (!hasForegroundPermission) {
            item {
                PermissionCard("Location permission is required. Tap Start Tracking to grant it.")
            }
        } else if (!hasBackgroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            item {
                PermissionCard("Background location permission is needed for tracking when the app is closed. Tap Start Tracking to grant it.")
            }
        }

        // Error message
        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Last recorded location
        if (recentLocations.isNotEmpty()) {
            item {
                val last = recentLocations.first()
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                val osmUrl = "https://www.openstreetmap.org/?mlat=${last.latitude}&mlon=${last.longitude}#map=16/${last.latitude}/${last.longitude}"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Last Recorded Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LocationRow("Time", dateFormat.format(Date(last.timestamp)))
                        LocationRow("Location", "%.6f, %.6f".format(last.latitude, last.longitude))
                        LocationRow("Altitude", last.altitude?.let { "%.1f m".format(it) } ?: "N/A")
                        LocationRow("Accuracy", last.accuracy?.let { "%.1f m".format(it) } ?: "N/A")
                        Text(
                            text = "View on OpenStreetMap",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(osmUrl))
                                    )
                                }
                        )
                    }
                }
            }
        }

        // Recent locations header + clear button
        if (recentLocations.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (locationCount > 10) "Recent Locations (last 10 of $locationCount)"
                        else "Recent Locations ($locationCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            items(recentLocations, key = { it.id }) { entity ->
                SavedLocationCard(entity)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PauseDurationDialog(
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    var customMinutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause Tracking") },
        text = {
            Column {
                Text(
                    text = "Select pause duration:",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val presets = listOf(15L, 30L, 60L, 120L)
                presets.forEach { minutes ->
                    val label = if (minutes >= 60) "${minutes / 60}h" else "${minutes}min"
                    OutlinedButton(
                        onClick = { onSelect(minutes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(label)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Or enter custom minutes (max 1440 / 24h):",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            customMinutes = value.filter { it.isDigit() }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val mins = customMinutes.toLongOrNull()
                            if (mins != null && mins in 1..1440) {
                                onSelect(mins)
                            }
                        },
                        enabled = customMinutes.toLongOrNull()?.let { it in 1..1440 } == true
                    ) {
                        Text("Go")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatRemainingTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
fun PermissionCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun SavedLocationCard(entity: LocationEntity) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val osmUrl = "https://www.openstreetmap.org/?mlat=${entity.latitude}&mlon=${entity.longitude}#map=16/${entity.latitude}/${entity.longitude}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(osmUrl))
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateFormat.format(Date(entity.timestamp)),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "%.5f, %.5f".format(entity.latitude, entity.longitude),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = entity.accuracy?.let { "%.0fm".format(it) } ?: "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun LocationRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
