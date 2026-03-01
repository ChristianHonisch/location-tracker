package com.loctracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        private val KEY_HIGH_ACCURACY = booleanPreferencesKey("high_accuracy")
        private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        private val KEY_TRACKING_STATE = stringPreferencesKey("tracking_state")
        private val KEY_RESUME_AT_MILLIS = longPreferencesKey("resume_at_millis")

        const val DEFAULT_INTERVAL_MINUTES = 10
        const val DEFAULT_HIGH_ACCURACY = true
        const val DEFAULT_AUTO_START_ON_BOOT = false
    }

    val intervalMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_INTERVAL_MINUTES] ?: DEFAULT_INTERVAL_MINUTES
    }

    val highAccuracy: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIGH_ACCURACY] ?: DEFAULT_HIGH_ACCURACY
    }

    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START_ON_BOOT] ?: DEFAULT_AUTO_START_ON_BOOT
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INTERVAL_MINUTES] = minutes.coerceIn(1, 60)
        }
    }

    suspend fun setHighAccuracy(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGH_ACCURACY] = enabled
        }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_START_ON_BOOT] = enabled
        }
    }

    // --- Tracking state persistence (survives process death) ---

    val trackingState: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TRACKING_STATE] ?: "STOPPED"
    }

    val resumeAtMillis: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_RESUME_AT_MILLIS] ?: 0L
    }

    suspend fun setTrackingState(state: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TRACKING_STATE] = state
        }
    }

    suspend fun setResumeAtMillis(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RESUME_AT_MILLIS] = millis
        }
    }
}
