# LocTracker - Still To Test

Items that couldn't be tested yet and need QA when possible.

## 1. Notification permission flow (Android 13+)

**Added:** Tier 1 fix (BUG-4)

**What to test:** On a device running Android 13 (API 33) or higher, start tracking for the very first time (or after clearing app data). The notification permission dialog should appear. Tracking should **not** start until after you respond to the dialog.

**Expected behavior:**
- Dialog appears asking for notification permission
- If you grant it: tracking starts, notification appears
- If you deny it: tracking starts anyway (notification permission is optional), but no persistent notification is shown

**Why it couldn't be tested:** No Android 13+ device available during QA session.

## 2. Pause duration cap (ISSUE-3)

**Added:** Tier 1 fix

**What to test:** In the pause dialog, enter a custom value greater than 1440 (e.g., 9999). The "Go" button should be **disabled**. Values 1-1440 should be accepted.

**Note:** The code fix is verified correct. If the button was enabled during testing, it was likely the old APK. Reinstall via Android Studio (Run > Run 'app') and re-test.
