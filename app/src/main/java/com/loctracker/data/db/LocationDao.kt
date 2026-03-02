package com.loctracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DaySummary(
    val dayMillis: Long,
    val pointCount: Int,
    val firstTimestamp: Long,
    val lastTimestamp: Long
)

@Dao
interface LocationDao {

    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT COUNT(*) FROM locations")
    fun getCount(): Flow<Int>

    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(limit: Int): Flow<List<LocationEntity>>

    @Query("DELETE FROM locations")
    suspend fun deleteAll()

    // --- History queries ---

    @Query("""
        SELECT 
            (timestamp / 86400000) * 86400000 AS dayMillis,
            COUNT(*) AS pointCount,
            MIN(timestamp) AS firstTimestamp,
            MAX(timestamp) AS lastTimestamp
        FROM locations 
        GROUP BY timestamp / 86400000 
        ORDER BY dayMillis DESC
    """)
    fun getDaySummaries(): Flow<List<DaySummary>>

    @Query("""
        SELECT * FROM locations 
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis 
        ORDER BY timestamp ASC
    """)
    fun getLocationsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<LocationEntity>>

    @Query("""
        DELETE FROM locations 
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis
    """)
    suspend fun deleteDay(dayStartMillis: Long, dayEndMillis: Long)

    @Query("SELECT * FROM locations ORDER BY timestamp ASC")
    suspend fun getAllLocationsOnce(): List<LocationEntity>

    @Query("""
        SELECT * FROM locations 
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis 
        ORDER BY timestamp ASC
    """)
    suspend fun getLocationsForDayOnce(dayStartMillis: Long, dayEndMillis: Long): List<LocationEntity>
}
