# LocTracker - Possible Improvements

## 1. Pause Resume: Switch from Skip-Save to AlarmManager

### Current Solution
During a pause, the foreground service keeps running and GPS location updates continue flowing. The `locationCallback` checks the pause state and **discards** incoming locations until the pause duration expires. When the next location update arrives after the pause deadline, the service auto-resumes and starts saving again.

**Pros:**
- Dead simple, no extra components
- 100% reliable — auto-resume happens naturally via the next location callback
- No Doze mode issues (the location updates keep the callback alive)

**Cons:**
- GPS stays active during the pause, consuming battery unnecessarily
- For a 2-hour pause at 1-min intervals, that's ~120 location fixes thrown away

### Better Solution
Use `AlarmManager.setExactAndAllowWhileIdle()` to schedule a resume alarm, and **stop** location updates during the pause. The alarm fires a `BroadcastReceiver` that sends `ACTION_RESUME` to the service, which then restarts location updates.

**Implementation:**
1. Add `SCHEDULE_EXACT_ALARM` permission to AndroidManifest (required API 31+)
2. Create an inner `BroadcastReceiver` class that receives the alarm intent
3. Register the receiver in AndroidManifest
4. On pause: call `fusedLocationClient.removeLocationUpdates()` + schedule the alarm
5. On alarm fire: receiver sends `ACTION_RESUME` intent to LocationService
6. On resume: call `startLocationUpdates()` again
7. Persist pause state + resume timestamp to SharedPreferences (survive process death)
8. On `onStartCommand` with null intent (system restart): read SharedPreferences and restore state

**Edge cases to handle:**
- Service killed and restarted by system during pause → read state from SharedPreferences
- Exact alarm permission denied on API 31+ → fall back to `setAndAllowWhileIdle()` (inexact)
- Doze mode may delay inexact alarms by up to 15 minutes

### Possible Gain
- **Battery**: Eliminating GPS activity during pause saves ~5-15 mW continuously (device-dependent). For a 2-hour pause, this could save 1-3% battery depending on the device and tracking interval.
- **Accuracy**: No gain — behavior is identical from the user's perspective.

### Estimated Effort
- **Complexity**: Medium
- **Files changed**: 2 (LocationService.kt, AndroidManifest.xml)
- **New files**: 0 (receiver can be an inner class)
- **Lines of code**: ~50-70 lines added/changed
- **Time estimate**: 1-2 hours including testing
- **Risk**: AlarmManager behavior varies across manufacturers (Samsung, Xiaomi, etc. have aggressive battery optimization that can block alarms). Testing on multiple devices recommended.
