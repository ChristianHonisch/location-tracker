# LocTracker - Project Goal

## Purpose

LocTracker is a privacy-first Android app for personal location tracking. It runs continuously in the background, recording the user's location at configurable intervals. All data stays on-device — no cloud, no analytics, no ads. The user has full control over their data.

## Core Requirements

### Background Location Tracking
- Runs as a foreground service with a persistent notification
- Tracks location every **10 minutes by default** (configurable: 1–60 min)
- Continues tracking until explicitly stopped by the user
- **Auto-starts on device boot** and resumes the previous tracking state
- Supports **"Pause for X minutes"** (15, 30, 60, 120 min, or custom) then auto-resumes

### Location Precision
- **Configurable precision**: toggle between high accuracy (GPS, ~10m) and balanced power (network/Wi-Fi, ~100m)
- Default: high accuracy (GPS) for best location precision

### Data Recorded Per Point
- Latitude
- Longitude
- Altitude
- Accuracy (meters)
- Timestamp
- Session ID (groups points by tracking session)

### Data Storage
- **Local only** — no network permissions, no data leaves the device
- SQLite database via Room (Jetpack)
- Data is kept indefinitely by default
- User can **optionally delete** individual sessions or all data

### Data Export
- Export to **GeoJSON** format
- Export all data or selected sessions
- Uses Android file picker to let the user choose the save location
- GeoJSON `FeatureCollection` with `Point` features; altitude, accuracy, and timestamp as properties

### User Interface
- **Home screen**: Start/stop tracking, pause for X minutes, current tracking status
- **History screen**: List of tracking sessions (date, duration, point count), tap to view individual points, export button
- **Settings screen**: Tracking interval, location precision toggle, auto-start on boot toggle
- **No map view** — list/log based, keeps the app simple and dependency-free

### Battery Optimization
- Batched location requests (`setMaxWaitTime`) to let the CPU sleep between updates
- Configurable precision (balanced power mode saves significant battery)
- No unnecessary wake locks
- No network activity
- Minimal UI updates when the app is in background

## Non-Goals
- No cloud sync or backup
- No map visualization
- No social features or sharing
- No ads, analytics, or telemetry
- No user accounts

## Technical Stack

| Component         | Technology                          |
|-------------------|-------------------------------------|
| Language           | Kotlin                              |
| Min SDK            | Android 8.0 (API 26)                |
| Target SDK         | Android 16 (API 36)                 |
| UI Framework       | Jetpack Compose (Material 3)        |
| Architecture       | MVVM                                |
| Database           | Room (SQLite)                       |
| Location API       | FusedLocationProviderClient         |
| Background         | Foreground Service                  |
| Build System       | Gradle (Kotlin DSL)                 |
| IDE                | Android Studio                      |

## Permissions

| Permission                           | Purpose                              |
|--------------------------------------|--------------------------------------|
| `ACCESS_FINE_LOCATION`               | GPS-level location (when enabled)    |
| `ACCESS_COARSE_LOCATION`             | Network-level location               |
| `ACCESS_BACKGROUND_LOCATION`         | Track while app is not visible       |
| `FOREGROUND_SERVICE`                 | Run persistent background service    |
| `FOREGROUND_SERVICE_LOCATION`        | Location-type foreground service (API 34+) |
| `POST_NOTIFICATIONS`                 | Show tracking notification (API 33+) |
| `RECEIVE_BOOT_COMPLETED`            | Auto-start tracking after reboot     |

## App Name

**LocTracker**

## Project Structure

```
app/src/main/java/com/loctracker/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt              # Room database definition
│   │   ├── LocationEntity.kt           # Location data model
│   │   └── LocationDao.kt              # Database queries
│   ├── repository/
│   │   └── LocationRepository.kt       # Data access layer
│   └── export/
│       └── GeoJsonExporter.kt          # GeoJSON export logic
├── service/
│   ├── LocationService.kt              # Foreground service for tracking
│   └── BootReceiver.kt                 # Auto-start on boot
├── ui/
│   ├── MainActivity.kt                 # Single activity host
│   ├── navigation/
│   │   └── NavGraph.kt                 # Navigation setup
│   ├── screens/
│   │   ├── HomeScreen.kt               # Start/stop, pause, status
│   │   ├── HistoryScreen.kt            # View/export tracked data
│   │   └── SettingsScreen.kt           # Interval, precision config
│   └── theme/
│       └── Theme.kt                    # Material 3 theme
├── viewmodel/
│   ├── HomeViewModel.kt
│   ├── HistoryViewModel.kt
│   └── SettingsViewModel.kt
└── LocTrackerApp.kt                    # Application class

app/src/main/res/
├── values/
│   └── strings.xml
└── drawable/
    └── ic_notification.xml             # Tracking notification icon
```

## Implementation Order

1. Create Android Studio project with Compose template
2. Set up Room database (entity, DAO, database)
3. Implement LocationRepository
4. Build the Foreground Service with location tracking
5. Build Home screen (start/stop/pause controls)
6. Build Settings screen (interval, precision, auto-start)
7. Build History screen (session list, detail view)
8. Implement GeoJSON export
9. Add BootReceiver for auto-start on boot
10. Handle edge cases (permissions denied, GPS disabled, etc.)
11. Test on real device and optimize battery consumption
