# LocTracker - Milestones

## Completed

### Milestone 1: Show My Location
- Single screen with "Get My Location" button
- Runtime location permission handling
- Fetches and displays current location (lat, lon, altitude, accuracy, timestamp)
- Tappable "View on OpenStreetMap" link

### Milestone 2: Save & List Locations
- Room database with `locations` table
- Each location fetch is saved to the database
- Scrollable list of all saved locations with OSM links
- "Clear All" button with confirmation dialog
- Data persists across app restarts

### Milestone 3: Background Tracking
- Foreground service with persistent notification
- Automatic location recording at a fixed interval (1 min for testing, 10 min default)
- Start/Stop tracking toggle on Home screen
- Status display (Tracking / Stopped)
- Last 10 locations shown, with "Showing last 10 of X" summary
- Background location permission flow (two-step on Android 10+)
- Notification permission flow (Android 13+)

### Milestone 4: Pause for X Minutes
- "Pause Tracking" button on Home screen (visible only while tracking)
- Duration picker dialog: preset buttons (15min, 30min, 1h, 2h) + custom input
- Service enters paused state (stops location requests but keeps running)
- Notification updates to show "Paused — resumes at HH:MM"
- Auto-resumes after the pause duration expires (via Handler)
- Home screen shows paused state with live countdown timer
- "Resume Now" button to manually end the pause early
- Service state exposed via StateFlow — UI always shows correct state even after reopening app
- Pause state survives app being closed (service handles the timer)

---

## Planned

### Milestone 5: Settings Screen
- Add bottom navigation: Home | History | Settings
- Settings screen with:
  - **Tracking interval**: slider or input, 1–60 minutes (default: 10)
  - **Location precision**: toggle between high accuracy (GPS) and balanced power (network)
  - **Auto-start on boot**: toggle (default: off)
- Persist settings using Jetpack DataStore (Preferences)
- LocationService reads settings and applies them dynamically
- Changing interval while tracking takes effect on next cycle
- Changing precision while tracking takes effect immediately

### Milestone 6: Boot Auto-Start
- `BootReceiver` registered in AndroidManifest for `BOOT_COMPLETED`
- On boot: checks if auto-start is enabled in settings
- If enabled and tracking was active before shutdown: restarts the foreground service
- Stores last tracking state (running/stopped) in DataStore
- Add `RECEIVE_BOOT_COMPLETED` permission to manifest
- Works reliably on Android 8+ with foreground service start restrictions

### Milestone 7: History & Export
- History screen (second tab in bottom navigation)
- Locations grouped by day:
  - Each row: date, number of points, first/last timestamp
  - Tappable to expand and show individual points for that day
  - Each point has an OSM link
- Export functionality:
  - Export all data or a selected day as GeoJSON
  - Uses `ACTION_CREATE_DOCUMENT` intent for file picker
  - GeoJSON `FeatureCollection` with `Point` features
  - Properties per point: altitude, accuracy, timestamp
- Delete functionality:
  - Delete individual days
  - Delete all data (moved here from Home screen)

---

## Future Ideas (Post-MVP)
- Embedded OpenStreetMap view (osmdroid or MapLibre)
- GPX/CSV/KML export format options
- Session-based grouping (in addition to day-based)
- Storage usage indicator
- Battery usage statistics
- Widget showing tracking status
- Dark/light theme toggle
