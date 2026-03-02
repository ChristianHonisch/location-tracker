# LocTracker - Known Issues

## Fixed

### ~~1. "Resume Now" button stays visible after pause timer expires~~ (Fixed)

**Fixed in:** Tier 1 bug fixes (BUG-3)

**Fix:** Added `ACTION_RESUME` intent from the UI's `LaunchedEffect` countdown when `remainingSeconds` reaches 0. The service now resumes immediately instead of waiting for the next location callback.

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
