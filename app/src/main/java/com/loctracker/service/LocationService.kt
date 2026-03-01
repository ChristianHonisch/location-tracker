package com.loctracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.loctracker.MainActivity
import com.loctracker.data.db.AppDatabase
import com.loctracker.data.db.LocationEntity
import com.loctracker.data.preferences.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationService : Service() {

    enum class TrackingState {
        STOPPED, TRACKING, PAUSED
    }

    companion object {
        const val ACTION_START = "com.loctracker.ACTION_START"
        const val ACTION_STOP = "com.loctracker.ACTION_STOP"
        const val ACTION_PAUSE = "com.loctracker.ACTION_PAUSE"
        const val ACTION_RESUME = "com.loctracker.ACTION_RESUME"
        const val ACTION_UPDATE_SETTINGS = "com.loctracker.ACTION_UPDATE_SETTINGS"

        const val EXTRA_PAUSE_DURATION_MS = "com.loctracker.EXTRA_PAUSE_DURATION_MS"

        const val CHANNEL_ID = "loctracker_tracking"
        const val NOTIFICATION_ID = 1

        private const val TAG = "LocationService"

        // Observable state for the Activity to observe
        private val _state = MutableStateFlow(TrackingState.STOPPED)
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        private val _resumeAtMillis = MutableStateFlow(0L)
        val resumeAtMillis: StateFlow<Long> = _resumeAtMillis.asStateFlow()

        // Current settings (observable by the Activity for display)
        private val _currentIntervalMinutes = MutableStateFlow(SettingsStore.DEFAULT_INTERVAL_MINUTES)
        val currentIntervalMinutes: StateFlow<Int> = _currentIntervalMinutes.asStateFlow()

        /**
         * Restore companion-object state from DataStore on cold start.
         * Called by MainActivity to ensure the UI shows the correct state
         * even after Android kills and recreates the app process.
         */
        suspend fun restoreStateFromDataStore(context: android.content.Context) {
            val store = SettingsStore(context)
            val savedState = store.trackingState.first()
            val savedResumeAt = store.resumeAtMillis.first()
            val state = try { TrackingState.valueOf(savedState) } catch (_: Exception) { TrackingState.STOPPED }

            // Only restore non-STOPPED states if the resume deadline hasn't long passed
            when (state) {
                TrackingState.TRACKING -> {
                    _state.value = TrackingState.TRACKING
                }
                TrackingState.PAUSED -> {
                    if (savedResumeAt > System.currentTimeMillis()) {
                        _state.value = TrackingState.PAUSED
                        _resumeAtMillis.value = savedResumeAt
                    } else {
                        // Pause already expired — show as tracking (service will confirm)
                        _state.value = TrackingState.TRACKING
                    }
                }
                TrackingState.STOPPED -> {
                    _state.value = TrackingState.STOPPED
                }
            }
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var settingsStore: SettingsStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun persistState(state: TrackingState, resumeAt: Long = _resumeAtMillis.value) {
        serviceScope.launch {
            settingsStore.setTrackingState(state.name)
            settingsStore.setResumeAtMillis(resumeAt)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsStore = SettingsStore(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // If paused, check if it's time to auto-resume
                    if (_state.value == TrackingState.PAUSED) {
                        if (System.currentTimeMillis() >= _resumeAtMillis.value) {
                            // Pause duration expired — auto-resume
                            _resumeAtMillis.value = 0L
                            _state.value = TrackingState.TRACKING
                            persistState(TrackingState.TRACKING, 0L)
                            updateNotification("Recording your location")
                            Log.d(TAG, "Auto-resumed from pause")
                        } else {
                            // Still paused — discard this location
                            Log.d(TAG, "Paused — discarding location")
                            return
                        }
                    }

                    Log.d(TAG, "Location received: ${location.latitude}, ${location.longitude}")
                    val dao = AppDatabase.getInstance(this@LocationService).locationDao()
                    serviceScope.launch {
                        dao.insert(
                            LocationEntity(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = if (location.hasAltitude()) location.altitude else null,
                                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                                timestamp = location.time
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Null intent means the system restarted us after process death (START_STICKY).
        // Restore state from DataStore and resume tracking.
        if (intent == null) {
            serviceScope.launch {
                val savedState = settingsStore.trackingState.first()
                val state = try { TrackingState.valueOf(savedState) } catch (_: Exception) { TrackingState.STOPPED }
                if (state != TrackingState.STOPPED) {
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, buildNotification("Recording your location"))
                    startLocationUpdates()
                    val savedResumeAt = settingsStore.resumeAtMillis.first()
                    if (state == TrackingState.PAUSED && savedResumeAt > System.currentTimeMillis()) {
                        _state.value = TrackingState.PAUSED
                        _resumeAtMillis.value = savedResumeAt
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        updateNotification("Paused — resumes at ${timeFormat.format(Date(savedResumeAt))}")
                    } else {
                        _state.value = TrackingState.TRACKING
                        _resumeAtMillis.value = 0L
                    }
                    Log.d(TAG, "Service restarted by system — restored state: ${_state.value}")
                } else {
                    stopSelf()
                }
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                if (_state.value == TrackingState.STOPPED) {
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, buildNotification("Recording your location"))
                    serviceScope.launch {
                        startLocationUpdates()
                    }
                    _state.value = TrackingState.TRACKING
                    persistState(TrackingState.TRACKING, 0L)
                }
            }
            ACTION_STOP -> stopTracking()
            ACTION_PAUSE -> {
                val durationMs = intent.getLongExtra(EXTRA_PAUSE_DURATION_MS, 0L)
                if (durationMs > 0 && _state.value == TrackingState.TRACKING) {
                    pauseTracking(durationMs)
                }
            }
            ACTION_RESUME -> {
                if (_state.value == TrackingState.PAUSED) {
                    resumeTracking()
                }
            }
            ACTION_UPDATE_SETTINGS -> {
                if (_state.value == TrackingState.TRACKING) {
                    // Restart location updates with new settings
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    serviceScope.launch {
                        startLocationUpdates()
                    }
                    Log.d(TAG, "Settings updated — restarting location updates")
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startLocationUpdates() {
        val intervalMinutes = settingsStore.intervalMinutes.first()
        val highAccuracy = settingsStore.highAccuracy.first()

        val intervalMs = intervalMinutes * 60 * 1000L
        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
                       else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        _currentIntervalMinutes.value = intervalMinutes

        val locationRequest = LocationRequest.Builder(
            priority,
            intervalMs
        ).apply {
            setMinUpdateIntervalMillis(intervalMs)
            setMaxUpdateDelayMillis(intervalMs * 2)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started: interval=${intervalMinutes}min, highAccuracy=$highAccuracy")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission: ${e.message}")
            stopSelf()
        }
    }

    private fun pauseTracking(durationMs: Long) {
        val resumeAt = System.currentTimeMillis() + durationMs
        _resumeAtMillis.value = resumeAt
        _state.value = TrackingState.PAUSED
        persistState(TrackingState.PAUSED, resumeAt)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val resumeTimeStr = timeFormat.format(Date(resumeAt))
        updateNotification("Paused — resumes at $resumeTimeStr")

        Log.d(TAG, "Tracking paused for ${durationMs / 1000 / 60} min, resumes at $resumeTimeStr")
    }

    private fun resumeTracking() {
        _resumeAtMillis.value = 0L
        _state.value = TrackingState.TRACKING
        persistState(TrackingState.TRACKING, 0L)
        updateNotification("Recording your location")
        Log.d(TAG, "Tracking resumed (manual)")
    }

    private fun stopTracking() {
        if (_state.value == TrackingState.STOPPED) return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        _resumeAtMillis.value = 0L
        _state.value = TrackingState.STOPPED
        persistState(TrackingState.STOPPED, 0L)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Tracking stopped")
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when LocTracker is recording your location"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocTracker")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (_state.value != TrackingState.STOPPED) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            _state.value = TrackingState.STOPPED
        }
        _resumeAtMillis.value = 0L
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
