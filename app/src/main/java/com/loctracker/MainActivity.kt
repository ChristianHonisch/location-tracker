package com.loctracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.loctracker.service.LocationService
import com.loctracker.service.LocationService.TrackingState
import com.loctracker.ui.screens.HistoryScreen
import com.loctracker.ui.screens.HomeScreen
import com.loctracker.ui.screens.SettingsScreen
import com.loctracker.ui.theme.LocTrackerTheme

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    HISTORY("History", Icons.Filled.Schedule),
    SETTINGS("Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocTrackerTheme {
                AppScaffold()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocTracker", fontWeight = FontWeight.Bold) }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedTab == screen,
                        onClick = { selectedTab = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            Screen.HOME -> HomeScreen(modifier = Modifier.padding(innerPadding))
            Screen.HISTORY -> HistoryScreen(modifier = Modifier.padding(innerPadding))
            Screen.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
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
