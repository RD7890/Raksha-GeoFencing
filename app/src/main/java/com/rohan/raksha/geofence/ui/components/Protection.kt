package com.rohan.raksha.geofence.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import kotlinx.coroutines.delay

/** What the tracking service is doing, read from the preferences it writes. */
data class ProtectionInfo(
    val level: Level = Level.OFF,
    val error: String = "",
    /** zone id -> "INSIDE" / "OUTSIDE" / "UNKNOWN" */
    val zoneStates: Map<Int, String> = emptyMap()
) {
    enum class Level { OFF, STARTING, ACTIVE, ERROR }
}

private fun read(prefs: SharedPreferences, zones: List<SavedLocation>): ProtectionInfo {
    val enabled = zones.filter { it.isEnabled }
    val states = zones.associate { it.id to (prefs.getString("zone_${it.id}", null) ?: "UNKNOWN") }
    if (enabled.isEmpty()) return ProtectionInfo(ProtectionInfo.Level.OFF, zoneStates = states)
    val error = prefs.getString("svc_error", "") ?: ""
    if (error.isNotEmpty()) return ProtectionInfo(ProtectionInfo.Level.ERROR, error, states)
    val beat = prefs.getLong("svc_heartbeat", 0L)
    val age = if (beat == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - beat) / 1000
    val level = if (age < 60) ProtectionInfo.Level.ACTIVE else ProtectionInfo.Level.STARTING
    return ProtectionInfo(level, zoneStates = states)
}

/** Polls the service status every 2 s while on screen. */
@Composable
fun rememberProtection(zones: List<SavedLocation>): ProtectionInfo {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE) }
    var info by remember { mutableStateOf(ProtectionInfo()) }
    LaunchedEffect(zones) {
        while (true) {
            info = read(prefs, zones)
            delay(2_000)
        }
    }
    return info
}
