# LocTracker - Known Issues

## 1. "Resume Now" button stays visible after pause timer expires

**Severity:** Low (cosmetic)

**Symptom:** When the pause countdown reaches 0:00, the UI continues showing the "Paused" state with the "Resume Now" button. It stays this way until the next location callback fires in the service (up to 1 tracking interval later), which flips the state back to TRACKING.

**Root Cause:** The auto-resume happens inside the service's `locationCallback`, which only fires when a new location arrives. The UI countdown in `MainActivity.kt` (`LaunchedEffect`) knows the timer has expired, but doesn't trigger the state change — it just displays 0:00 and waits for the service to catch up.

**Proposed Fix:** In the `LaunchedEffect` countdown block in `MainActivity.kt`, when `remainingSeconds` reaches 0, send an `ACTION_RESUME` intent to the service. This forces an immediate state flip rather than waiting for the next location callback. The service's `resumeTracking()` already handles this correctly, so no service changes are needed.

```kotlin
// In the LaunchedEffect block, after the while loop:
if (trackingState == TrackingState.PAUSED) {
    // Timer expired but service hasn't auto-resumed yet — nudge it
    val intent = Intent(context, LocationService::class.java).apply {
        action = LocationService.ACTION_RESUME
    }
    context.startService(intent)
}
```

**Estimated Effort:** ~5 minutes, 1 file changed (`MainActivity.kt`)
