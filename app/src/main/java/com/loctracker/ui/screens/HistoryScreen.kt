package com.loctracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loctracker.data.db.AppDatabase
import com.loctracker.data.db.DaySummary
import com.loctracker.data.db.LocationEntity
import com.loctracker.data.export.GeoJsonExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MS_PER_DAY = 86400000L

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.locationDao() }
    val scope = rememberCoroutineScope()

    val daySummaries by dao.getDaySummaries().collectAsState(initial = emptyList())
    val locationCount by dao.getCount().collectAsState(initial = 0)

    // Track which days are expanded
    var expandedDays by remember { mutableStateOf(setOf<Long>()) }

    // Delete confirmation dialogs
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var deleteDayTarget by remember { mutableStateOf<DaySummary?>(null) }

    // Export state: null = export all, non-null = export specific day
    var pendingExportDayMillis by remember { mutableStateOf<Long?>(null) }
    var exportAllRequested by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val locations = withContext(Dispatchers.IO) {
                        if (exportAllRequested) {
                            dao.getAllLocationsOnce()
                        } else {
                            val dayStart = pendingExportDayMillis ?: return@withContext emptyList()
                            dao.getLocationsForDayOnce(dayStart, dayStart + MS_PER_DAY)
                        }
                    }
                    if (locations.isEmpty()) {
                        Toast.makeText(context, "No locations to export", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            GeoJsonExporter.export(locations, outputStream)
                        }
                    }
                    Toast.makeText(context, "Exported ${locations.size} points", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        // Reset export state
        exportAllRequested = false
        pendingExportDayMillis = null
    }

    fun startExportAll() {
        exportAllRequested = true
        pendingExportDayMillis = null
        exportLauncher.launch("loctracker_all.geojson")
    }

    fun startExportDay(dayMillis: Long) {
        exportAllRequested = false
        pendingExportDayMillis = dayMillis
        val dayStr = dateFormat.format(Date(dayMillis))
        exportLauncher.launch("loctracker_$dayStr.geojson")
    }

    // Delete All confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Locations") },
            text = { Text("Delete all $locationCount saved locations? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { dao.deleteAll() }
                        expandedDays = emptySet()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Day confirmation dialog
    deleteDayTarget?.let { daySummary ->
        val dayStr = dateFormat.format(Date(daySummary.dayMillis))
        AlertDialog(
            onDismissRequest = { deleteDayTarget = null },
            title = { Text("Delete Day") },
            text = { Text("Delete all ${daySummary.pointCount} locations from $dayStr? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.deleteDay(daySummary.dayMillis, daySummary.dayMillis + MS_PER_DAY)
                        }
                        expandedDays = expandedDays - daySummary.dayMillis
                        deleteDayTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDayTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Empty state
    if (daySummaries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No Locations Recorded",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Start tracking from the Home tab to begin\nrecording your location history.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top action row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { startExportAll() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export All")
                }
                OutlinedButton(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete All")
                }
            }
        }

        // Summary
        item {
            Text(
                text = "$locationCount points across ${daySummaries.size} day${if (daySummaries.size != 1) "s" else ""}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Day groups
        items(daySummaries, key = { it.dayMillis }) { daySummary ->
            DayCard(
                daySummary = daySummary,
                isExpanded = daySummary.dayMillis in expandedDays,
                onToggleExpand = {
                    expandedDays = if (daySummary.dayMillis in expandedDays) {
                        expandedDays - daySummary.dayMillis
                    } else {
                        expandedDays + daySummary.dayMillis
                    }
                },
                onExportDay = { startExportDay(daySummary.dayMillis) },
                onDeleteDay = { deleteDayTarget = daySummary },
                dateFormat = dateFormat,
                timeFormat = timeFormat
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DayCard(
    daySummary: DaySummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onExportDay: () -> Unit,
    onDeleteDay: () -> Unit,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.locationDao() }

    val dayStr = dateFormat.format(Date(daySummary.dayMillis))
    val firstTime = timeFormat.format(Date(daySummary.firstTimestamp))
    val lastTime = timeFormat.format(Date(daySummary.lastTimestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Header — tappable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dayStr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${daySummary.pointCount} points  |  $firstTime – $lastTime",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                val dayLocations by dao.getLocationsForDay(
                    daySummary.dayMillis,
                    daySummary.dayMillis + MS_PER_DAY
                ).collectAsState(initial = emptyList())

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    dayLocations.forEach { location ->
                        LocationPointRow(location = location, timeFormat = timeFormat)
                    }

                    // Day action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onExportDay,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Day", fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = onDeleteDay,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Day", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationPointRow(
    location: LocationEntity,
    timeFormat: SimpleDateFormat
) {
    val context = LocalContext.current
    val osmUrl = "https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=16/${location.latitude}/${location.longitude}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(osmUrl))
                )
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormat.format(Date(location.timestamp)),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.width(50.dp)
        )
        Text(
            text = "%.5f, %.5f".format(location.latitude, location.longitude),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = location.accuracy?.let { "%.0fm".format(it) } ?: "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(40.dp)
        )
    }
}
