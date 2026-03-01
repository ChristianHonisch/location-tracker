# LocTracker - Known Issues

## Fixed

### ~~1. "Resume Now" button stays visible after pause timer expires~~ (Fixed)

**Fixed in:** Tier 1 bug fixes (BUG-3)

**Fix:** Added `ACTION_RESUME` intent from the UI's `LaunchedEffect` countdown when `remainingSeconds` reaches 0. The service now resumes immediately instead of waiting for the next location callback.

---

## Open Issues

_No open issues at this time._
