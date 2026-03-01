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
