package com.rohan.raksha.geofence.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.notifications.NotificationHelper
import com.rohan.raksha.geofence.util.DistanceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Runs whenever at least one safe zone is enabled (started by the app, by boot, and as a
 * backup by the outer-ring geofence). Streams GPS fixes, compares them with the exact
 * radius and raises the full-screen alert when the user is confirmed to be outside.
 * Battery: high accuracy only while the user is near a zone, balanced power when far away.
 */
class TrackingService : Service() {

    private enum class ZoneState { UNKNOWN, INSIDE, OUTSIDE }

    // ordinal order matters: the highest mode of all zones wins
    private enum class Mode { FAR, ALERTED, ACTIVE }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private val tracked = linkedSetOf<Int>()
    private val exitCounters = mutableMapOf<Int, Int>()
    private var currentMode: Mode? = null

    private lateinit var notifications: NotificationHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var fused: FusedLocationProviderClient

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { mutex.withLock { process(location) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationHelper(this)
        prefs = getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called quickly after startForegroundService()
        startAsForeground()

        val id = intent?.getIntExtra(EXTRA_LOCATION_ID, -1) ?: -1
        val action = intent?.action

        scope.launch {
            mutex.withLock {
                if (tracked.isEmpty()) restoreTracked()
                when (action) {
                    ACTION_TRACK -> if (id != -1) tracked.add(id)
                    ACTION_OUTER_EXIT -> if (id != -1) onLeftOuterRing(id)
                }
                persistTracked()
                if (tracked.isEmpty()) {
                    shutdown()
                } else if (currentMode == null) {
                    setMode(Mode.ACTIVE) // high accuracy until the first fix tells us how far we are
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = notifications.createTrackingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                GeofenceConstants.NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(GeofenceConstants.NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    // ---- location handling -------------------------------------------------------------

    private suspend fun process(fix: Location) {
        if (tracked.isEmpty()) return
        val dao = AppDatabase.getInstance(this).savedLocationDao()
        var mode = Mode.FAR
        val lines = mutableListOf<String>()
        val accuracy = if (fix.hasAccuracy()) fix.accuracy else Float.MAX_VALUE

        for (id in tracked.toList()) {
            val saved = dao.getById(id)
            if (saved == null || !saved.isEnabled) {
                drop(id)
                continue
            }
            val distance = DistanceUtil.distanceBetween(
                fix.latitude, fix.longitude, saved.latitude, saved.longitude
            )
            evaluate(saved, distance, accuracy)

            val outerRing = GeofenceConstants.outerRingFor(saved.exitRadiusMeters)
            val zoneMode = when {
                distance > outerRing -> Mode.FAR
                zoneState(id) == ZoneState.OUTSIDE -> Mode.ALERTED
                else -> Mode.ACTIVE
            }
            if (zoneMode > mode) mode = zoneMode

            lines.add(
                "${saved.name}: ${zoneState(id).name}, ${distance.toInt()} m from centre, " +
                    "radius ${saved.exitRadiusMeters.toInt()} m, GPS ±${accuracy.coerceAtMost(999f).toInt()} m"
            )
        }
        recordStatus(lines)
        persistTracked()

        if (tracked.isEmpty()) shutdown() else setMode(mode)
    }

    private fun evaluate(saved: SavedLocation, distance: Float, accuracy: Float) {
        val id = saved.id
        val radius = saved.exitRadiusMeters
        val margin = min(accuracy, GeofenceConstants.ACCURACY_MARGIN_CAP_METERS)

        when (zoneState(id)) {
            ZoneState.INSIDE -> {
                val trusted = accuracy <= GeofenceConstants.MAX_ACCURACY_METERS
                // outside with a good fix, or clearly outside even with a bad fix
                val outside = (trusted && distance > radius + margin) || (distance - accuracy > radius)
                if (outside) {
                    val count = (exitCounters[id] ?: 0) + 1
                    if (count >= GeofenceConstants.CONFIRM_EXIT_FIXES) {
                        exitCounters.remove(id)
                        setZoneState(id, ZoneState.OUTSIDE)
                        Log.d(TAG, "Zone $id exit confirmed at ${distance.toInt()} m")
                        notifications.showExitAlert(saved)
                    } else {
                        exitCounters[id] = count
                    }
                } else {
                    exitCounters.remove(id)
                }
            }
            ZoneState.OUTSIDE, ZoneState.UNKNOWN -> {
                // one reasonable fix inside the circle (re)arms the zone
                if (distance <= radius && accuracy <= GeofenceConstants.ARM_MAX_ACCURACY_METERS) {
                    val wasOutside = zoneState(id) == ZoneState.OUTSIDE
                    exitCounters.remove(id)
                    setZoneState(id, ZoneState.INSIDE)
                    if (wasOutside) notifications.cancelAlert(id)
                }
            }
        }
    }

    /** Backup: Play Services says we left the outer ring. If we never confirmed the exit, alert now. */
    private suspend fun onLeftOuterRing(id: Int) {
        if (zoneState(id) == ZoneState.INSIDE) {
            val saved = AppDatabase.getInstance(this).savedLocationDao().getById(id)
            if (saved != null && saved.isEnabled) {
                setZoneState(id, ZoneState.OUTSIDE)
                exitCounters.remove(id)
                notifications.showExitAlert(saved)
            }
        }
    }

    private fun drop(id: Int) {
        tracked.remove(id)
        exitCounters.remove(id)
        setZoneState(id, ZoneState.UNKNOWN)
        notifications.cancelAlert(id)
    }

    // ---- location updates --------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun setMode(mode: Mode) {
        if (currentMode == mode) return
        currentMode = mode

        val request = when (mode) {
            Mode.ACTIVE ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.ACTIVE_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(1_000L)
                    .setMinUpdateDistanceMeters(GeofenceConstants.ACTIVE_MIN_DISTANCE_M)
                    .build()
            Mode.ALERTED ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.IDLE_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GeofenceConstants.IDLE_MIN_DISTANCE_M)
                    .build()
            Mode.FAR ->
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, GeofenceConstants.FAR_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GeofenceConstants.FAR_MIN_DISTANCE_M)
                    .build()
        }
        Log.d(TAG, "Location mode -> $mode")
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun shutdown() {
        fused.removeLocationUpdates(callback)
        currentMode = null
        prefs.edit().remove("status_summary").remove("status_time").apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- state ---------------------------------------------------------------------------

    private fun recordStatus(lines: List<String>) {
        prefs.edit()
            .putString("status_summary", lines.joinToString("\n"))
            .putLong("status_time", System.currentTimeMillis())
            .apply()
    }

    private fun zoneState(id: Int): ZoneState =
        try {
            ZoneState.valueOf(prefs.getString("zone_$id", null) ?: ZoneState.UNKNOWN.name)
        } catch (e: IllegalArgumentException) {
            ZoneState.UNKNOWN
        }

    private fun setZoneState(id: Int, state: ZoneState) {
        prefs.edit().putString("zone_$id", state.name).apply()
    }

    private fun persistTracked() {
        prefs.edit().putStringSet(KEY_TRACKED, tracked.map { it.toString() }.toSet()).apply()
    }

    private fun restoreTracked() {
        prefs.getStringSet(KEY_TRACKED, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.let { tracked.addAll(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fused.removeLocationUpdates(callback)
        scope.cancel()
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val ACTION_TRACK = "com.rohan.raksha.geofence.ACTION_TRACK"
        private const val ACTION_OUTER_EXIT = "com.rohan.raksha.geofence.ACTION_OUTER_EXIT"
        private const val EXTRA_LOCATION_ID = "location_id"
        private const val KEY_TRACKED = "tracked_ids"

        /** Make sure the service is running and watching this zone. */
        fun track(context: Context, locationId: Int) = send(context, ACTION_TRACK, locationId)

        /** Outer geofence ring was left (backup trigger). */
        fun outerExit(context: Context, locationId: Int) = send(context, ACTION_OUTER_EXIT, locationId)

        private fun send(context: Context, action: String, locationId: Int) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(action)
                .putExtra(EXTRA_LOCATION_ID, locationId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start tracking service", e)
            }
        }
    }
}
