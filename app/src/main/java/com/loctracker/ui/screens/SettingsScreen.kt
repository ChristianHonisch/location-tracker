package com.loctracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loctracker.data.preferences.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSettingsChanged: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val intervalMinutes by settingsStore.intervalMinutes.collectAsState(
        initial = SettingsStore.DEFAULT_INTERVAL_MINUTES
    )
    val highAccuracy by settingsStore.highAccuracy.collectAsState(
        initial = SettingsStore.DEFAULT_HIGH_ACCURACY
    )
    val autoStartOnBoot by settingsStore.autoStartOnBoot.collectAsState(
        initial = SettingsStore.DEFAULT_AUTO_START_ON_BOOT
    )

    // Local slider state to avoid jitter while dragging
    var sliderValue by remember(intervalMinutes) { mutableFloatStateOf(intervalMinutes.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Tracking Interval
        Text(
            text = "Tracking Interval",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "How often to record a location point.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val newValue = sliderValue.roundToInt()
                    scope.launch {
                        settingsStore.setIntervalMinutes(newValue)
                        onSettingsChanged()
                    }
                },
                valueRange = 1f..60f,
                steps = 58, // 1 to 60 = 59 values, 58 steps between them
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${sliderValue.roundToInt()} min",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.width(56.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // High Accuracy (GPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "High Accuracy (GPS)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (highAccuracy)
                        "Using GPS for ~10m accuracy. Higher battery usage."
                    else
                        "Using network/Wi-Fi for ~100m accuracy. Lower battery usage.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = highAccuracy,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsStore.setHighAccuracy(enabled)
                        onSettingsChanged()
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Auto-start on Boot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-start on Boot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Automatically resume tracking after the device restarts.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = autoStartOnBoot,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsStore.setAutoStartOnBoot(enabled)
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Info
        Text(
            text = "Changes to interval and precision take effect on the next tracking cycle. If tracking is active, it will be restarted with the new settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
