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
- Service enters paused state (discards incoming locations but keeps running)
- Notification updates to show "Paused — resumes at HH:MM"
- Auto-resumes after the pause duration expires (via locationCallback)
- Home screen shows paused state with live countdown timer
- "Resume Now" button to manually end the pause early
- Service state exposed via StateFlow — UI always shows correct state even after reopening app
- Pause state survives app being closed (service handles the timer)

### Milestone 5a: Settings Screen + DataStore
- Settings screen accessible via gear icon in top app bar
- Back arrow navigation to return to Home
- Settings options:
  - **Tracking interval**: slider, 1–60 minutes (default: 10)
  - **Location precision**: switch toggle between high accuracy (GPS) and balanced power (network)
  - **Auto-start on boot**: switch toggle (default: off, UI only — implementation in Milestone 6)
- Settings persisted using Jetpack DataStore (Preferences)
- LocationService reads interval + precision from DataStore on start and on settings change
- `ACTION_UPDATE_SETTINGS` intent restarts location updates with new settings while tracking
- Status card on Home screen displays the actual configured interval
- Removed hardcoded `TRACKING_INTERVAL_MS` constant — interval is now fully configurable

### Milestone 5b: Bottom Navigation
- Material 3 `NavigationBar` with three tabs: Home | History | Settings
- State-based navigation (no Jetpack Navigation dependency — simple `when` block)
- Replaced gear icon + back arrow with proper tab navigation
- Settings screen is now a tab (no `onBack` parameter, no back arrow)
- Placeholder History screen showing total point count (full implementation in Milestone 7)
- Extracted `HomeScreen` from `MainActivity.kt` into `ui/screens/HomeScreen.kt` (~550 lines)
- `MainActivity.kt` slimmed to ~85 lines (navigation scaffold only)
- Top app bar shared across all tabs with "LocTracker" title

### Code Review Tier 1 Fixes (applied alongside 5a)
- BUG-2: Guard `ACTION_UPDATE_SETTINGS` — only send if service is not stopped
- BUG-3: Send `ACTION_RESUME` from UI when pause countdown reaches 0
- BUG-4: Notification permission race — return after launch, start from callback
- ISSUE-1: Persist tracking state to DataStore (UI survives process death)
- ISSUE-3: Cap custom pause at 1440 min (24h) in UI + defensive cap in service
- DOC fixes: goal.md precision default, MILESTONES.md wording, KNOWN_ISSUES.md updates

---

### Milestone 6: Boot Auto-Start
- `BootReceiver` registered in AndroidManifest for `BOOT_COMPLETED`
- `RECEIVE_BOOT_COMPLETED` permission added to manifest
- On boot: reads `autoStartOnBoot` and `trackingState` from DataStore
- Restarts foreground service only if auto-start is enabled **and** tracking was previously active (`TRACKING` or `PAUSED`)
- Respects explicit stop — if user stopped tracking before reboot, it stays stopped
- Uses `runBlocking` for DataStore reads in receiver (standard pattern, microsecond reads)
- Tracking state already persisted to DataStore (from Tier 1 ISSUE-1 fix)
- Works on Android 8+ with `startForegroundService()`

---

## Planned

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
