package com.loctracker.data.export

import com.loctracker.data.db.LocationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Exports a list of LocationEntity to GeoJSON format.
 *
 * Output format: GeoJSON FeatureCollection with Point features.
 * Each feature has:
 *   - geometry: Point with [longitude, latitude] or [longitude, latitude, altitude]
 *   - properties: timestamp (ISO 8601), accuracy (meters)
 *
 * Uses org.json (built into Android, no external dependency).
 */
object GeoJsonExporter {

    // DateTimeFormatter is immutable and thread-safe (unlike SimpleDateFormat)
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun export(locations: List<LocationEntity>, outputStream: OutputStream) {
        val featureCollection = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                for (location in locations) {
                    put(buildFeature(location))
                }
            })
        }

        outputStream.bufferedWriter().use { writer ->
            writer.write(featureCollection.toString(2))
        }
    }

    private fun buildFeature(location: LocationEntity): JSONObject {
        val coordinates = JSONArray().apply {
            put(location.longitude)
            put(location.latitude)
            if (location.altitude != null) {
                put(location.altitude)
            }
        }

        val geometry = JSONObject().apply {
            put("type", "Point")
            put("coordinates", coordinates)
        }

        val properties = JSONObject().apply {
            put("timestamp", isoFormatter.format(Instant.ofEpochMilli(location.timestamp)))
            if (location.accuracy != null) {
                put("accuracy", location.accuracy.toDouble())
            }
            if (location.altitude != null) {
                put("altitude", location.altitude)
            }
        }

        return JSONObject().apply {
            put("type", "Feature")
            put("geometry", geometry)
            put("properties", properties)
        }
    }
}
