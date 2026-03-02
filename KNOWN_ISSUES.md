# LocTracker - Known Issues

## Fixed

### ~~1. "Resume Now" button stays visible after pause timer expires~~ (Fixed)

**Fixed in:** Tier 1 bug fixes (BUG-3)

**Fix:** Added `ACTION_RESUME` intent from the UI's `LaunchedEffect` countdown when `remainingSeconds` reaches 0. The service now resumes immediately instead of waiting for the next location callback.

### ~~4. startForeground() crash on system restart (Android 12+)~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Symptom:** On Android 12+, the system could kill the service on process-death restart because `startForeground()` was called inside a coroutine (after an async DataStore read), potentially exceeding the 5-second deadline.

**Fix:** Moved `createNotificationChannel()` and `startForeground()` to run synchronously in `onStartCommand()` before launching the async DataStore restore coroutine.

### ~~5. onDestroy didn't persist STOPPED state~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Symptom:** After the service was destroyed, DataStore still contained TRACKING/PAUSED state. On next cold start, the app would incorrectly think tracking was still active.

**Fix:** Added `runBlocking` persist of STOPPED state in `onDestroy()` before cancelling the coroutine scope.

### ~~6. SimpleDateFormat thread-safety in GeoJsonExporter~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Fix:** Replaced `SimpleDateFormat` singleton with thread-safe `java.time.format.DateTimeFormatter`.

### ~~7. No Room migration strategy~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Fix:** Added `fallbackToDestructiveMigration(dropAllTables = true)` to `AppDatabase` builder. Schema changes will wipe data instead of crashing.

### ~~8. BootReceiver always sent ACTION_START, losing PAUSED state~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Fix:** BootReceiver now sends an action-less intent, triggering the service's DataStore restore path which correctly restores TRACKING or PAUSED state.

### ~~9. History day grouping used UTC, UI displayed local time~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Symptom:** Points recorded near midnight could appear under the wrong day in the History tab.

**Fix:** `getDaySummaries()` now takes a timezone offset parameter. HistoryScreen passes the device's current UTC offset (including DST).

### ~~10. Expanded day rendered all points at once (jank risk)~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Fix:** Expanded days now show a maximum of 50 points initially, with a "Show all N points" button to load the rest.

### ~~11. allowBackup leaked location database~~ (Fixed)

**Fixed in:** Review 2 — Tier 1 fixes

**Fix:** Updated `backup_rules.xml` and `data_extraction_rules.xml` to exclude `loctracker.db` (and WAL/SHM files) from cloud backup and device transfer. Settings (DataStore) are still backed up.

---

## Open Issues

### 2. Location interval is roughly double the configured value

**Severity:** Low (expected behavior)

**Symptom:** Setting a 1-minute tracking interval results in locations being recorded approximately every 2 minutes. A 2-minute setting logs roughly every 3 minutes.

**Root Cause:** `FusedLocationProviderClient` treats the configured interval as a **minimum**, not an exact target. Google Play Services batches and throttles location updates for battery efficiency. The actual interval depends on device state, other apps requesting location, Doze mode, and battery optimizations.

**Workaround:** None needed. This is standard Android behavior. If precise 1-minute intervals are critical, an `AlarmManager`-based approach could be used instead (tracked in `POSSIBLE_IMPROVEMENTS.md`), but that would increase battery usage.

**Priority:** Low / won't fix — this is by design in the Android location API.

### 3. Boot auto-start may not work on Samsung and other OEM devices

**Severity:** Medium (device-specific)

**Symptom:** After enabling "Auto-start on Boot" and rebooting the phone, tracking does not resume automatically. The notification only appears after opening the app manually.

**Root Cause:** Samsung (One UI), Xiaomi (MIUI/HyperOS), Huawei (EMUI), and other manufacturers apply aggressive battery optimization that can suppress `BOOT_COMPLETED` broadcast delivery to apps not on their whitelist. This is an OEM-specific restriction, not an Android platform limitation — `BOOT_COMPLETED` is officially exempt from Android 12+'s background foreground-service start restrictions.

**Workaround (Samsung):**
1. Go to **Settings > Apps > LocTracker > Battery** and set to **Unrestricted**
2. Go to **Settings > Battery > Background usage limits > Never sleeping apps** and add **LocTracker**

**Workaround (Xiaomi/MIUI):**
1. Go to **Settings > Apps > Manage apps > LocTracker > Autostart** and enable it
2. Go to **Settings > Battery > App battery saver > LocTracker** and set to **No restrictions**

**Workaround (Huawei/EMUI):**
1. Go to **Settings > Battery > App launch** and set LocTracker to **Manage manually** with all toggles enabled

**Reference:** [dontkillmyapp.com](https://dontkillmyapp.com/) — comprehensive list of device-specific battery optimization workarounds.

**Priority:** Medium — the code is correct; this requires user action on affected devices. A helper note is shown in the Settings UI when the auto-start toggle is enabled.
