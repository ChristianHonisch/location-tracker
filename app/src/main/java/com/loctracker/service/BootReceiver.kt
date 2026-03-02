package com.loctracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.loctracker.data.preferences.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Receives BOOT_COMPLETED broadcast and restarts location tracking
 * if auto-start is enabled and tracking was previously active.
 *
 * Uses runBlocking to read DataStore — this is acceptable here because
 * DataStore preferences reads are cached in memory and complete in
 * microseconds after the first access.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val settingsStore = SettingsStore(context)

        val autoStart = runBlocking { settingsStore.autoStartOnBoot.first() }
        val savedState = runBlocking { settingsStore.trackingState.first() }

        Log.d(TAG, "Boot completed — autoStart=$autoStart, savedState=$savedState")

        if (!autoStart) {
            Log.d(TAG, "Auto-start is disabled — not starting service")
            return
        }

        if (savedState != "TRACKING" && savedState != "PAUSED") {
            Log.d(TAG, "Tracking was not active before reboot — not starting service")
            return
        }

        // Send intent WITHOUT an action so that the null-intent / restore path
        // in onStartCommand() runs.  This correctly restores the exact state
        // (TRACKING or PAUSED with the saved resumeAt timestamp) from DataStore
        // instead of always starting fresh as TRACKING.
        Log.d(TAG, "Starting location service after boot (restore path)")
        val serviceIntent = Intent(context, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
