package com.loctracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        const val EXTRA_PAUSE_DURATION_MS = "com.loctracker.EXTRA_PAUSE_DURATION_MS"

        const val CHANNEL_ID = "loctracker_tracking"
        const val NOTIFICATION_ID = 1

        // 1 minute for testing — change to 10 * 60 * 1000L for production
        const val TRACKING_INTERVAL_MS = 1 * 60 * 1000L

        private const val TAG = "LocationService"

        // Observable state for the Activity to observe
        private val _state = MutableStateFlow(TrackingState.STOPPED)
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        private val _resumeAtMillis = MutableStateFlow(0L)
        val resumeAtMillis: StateFlow<Long> = _resumeAtMillis.asStateFlow()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var resumeRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
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
        when (intent?.action) {
            ACTION_START -> {
                if (_state.value == TrackingState.STOPPED) {
                    createNotificationChannel()
                    startForeground(NOTIFICATION_ID, buildNotification("Recording your location"))
                    startLocationUpdates()
                    _state.value = TrackingState.TRACKING
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
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TRACKING_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(TRACKING_INTERVAL_MS)
            setMaxUpdateDelayMillis(TRACKING_INTERVAL_MS * 2)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started with interval ${TRACKING_INTERVAL_MS / 1000}s")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission: ${e.message}")
            stopSelf()
        }
    }

    private fun pauseTracking(durationMs: Long) {
        // Stop location updates but keep service alive
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val resumeAt = System.currentTimeMillis() + durationMs
        _resumeAtMillis.value = resumeAt
        _state.value = TrackingState.PAUSED

        // Update notification
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val resumeTimeStr = timeFormat.format(Date(resumeAt))
        updateNotification("Paused — resumes at $resumeTimeStr")

        // Schedule auto-resume
        resumeRunnable?.let { handler.removeCallbacks(it) }
        resumeRunnable = Runnable { resumeTracking() }
        handler.postDelayed(resumeRunnable!!, durationMs)

        Log.d(TAG, "Tracking paused for ${durationMs / 1000 / 60} min, resumes at $resumeTimeStr")
    }

    private fun resumeTracking() {
        // Cancel any pending resume timer
        resumeRunnable?.let { handler.removeCallbacks(it) }
        resumeRunnable = null
        _resumeAtMillis.value = 0L

        // Restart location updates
        startLocationUpdates()
        _state.value = TrackingState.TRACKING

        updateNotification("Recording your location")
        Log.d(TAG, "Tracking resumed")
    }

    private fun stopTracking() {
        if (_state.value == TrackingState.STOPPED) return

        // Cancel any pending resume timer
        resumeRunnable?.let { handler.removeCallbacks(it) }
        resumeRunnable = null
        _resumeAtMillis.value = 0L

        fusedLocationClient.removeLocationUpdates(locationCallback)
        _state.value = TrackingState.STOPPED
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
        resumeRunnable?.let { handler.removeCallbacks(it) }
        if (_state.value != TrackingState.STOPPED) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            _state.value = TrackingState.STOPPED
        }
        _resumeAtMillis.value = 0L
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
