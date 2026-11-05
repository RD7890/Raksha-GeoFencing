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
 * Started by the outer-ring geofence (ENTER). Streams GPS fixes and compares them with the
 * exact safe-zone radius. When the user is confirmed to be outside, it raises the
 * full-screen alert. Stops itself when no safe zone is being tracked any more.
 */
class TrackingService : Service() {

    private enum class ZoneState { UNKNOWN, INSIDE, OUTSIDE }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private val tracked = linkedSetOf<Int>()
    private val exitCounters = mutableMapOf<Int, Int>()
    private var activeMode: Boolean? = null

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
                    ACTION_TRACK -> if (id != -1) {
                        tracked.add(id)
                        exitCounters.remove(id)
                    }
                    ACTION_UNTRACK -> if (id != -1) onLeftOuterRing(id)
                }
                persistTracked()
                if (tracked.isEmpty()) shutdown() else setMode(tracked.any { zoneState(it) != ZoneState.OUTSIDE })
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

    private suspend fun process(location: Location) {
        if (tracked.isEmpty()) return
        val dao = AppDatabase.getInstance(this).savedLocationDao()
        for (id in tracked.toList()) {
            val saved = dao.getById(id)
            if (saved == null || !saved.isEnabled) {
                drop(id)
                continue
            }
            evaluate(saved, location)
        }
        persistTracked()
        if (tracked.isEmpty()) {
            shutdown()
        } else {
            setMode(tracked.any { zoneState(it) != ZoneState.OUTSIDE })
        }
    }

    private fun evaluate(saved: SavedLocation, fix: Location) {
        val accuracy = if (fix.hasAccuracy()) fix.accuracy else Float.MAX_VALUE
        if (accuracy > GeofenceConstants.MAX_ACCURACY_METERS) return // too noisy to trust

        val distance = DistanceUtil.distanceBetween(
            fix.latitude, fix.longitude, saved.latitude, saved.longitude
        )
        val radius = saved.exitRadiusMeters
        val margin = min(accuracy, GeofenceConstants.ACCURACY_MARGIN_CAP_METERS)
        val id = saved.id

        when (zoneState(id)) {
            ZoneState.INSIDE -> {
                if (distance > radius + margin) {
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
                // One good fix inside the circle is enough to (re)arm the zone
                if (distance <= radius) {
                    val wasOutside = zoneState(id) == ZoneState.OUTSIDE
                    exitCounters.remove(id)
                    setZoneState(id, ZoneState.INSIDE)
                    if (wasOutside) notifications.cancelAlert(id)
                }
            }
        }
    }

    /** The phone left the outer ring. If we never confirmed the exit, alert now. */
    private suspend fun onLeftOuterRing(id: Int) {
        if (zoneState(id) == ZoneState.INSIDE) {
            val saved = AppDatabase.getInstance(this).savedLocationDao().getById(id)
            if (saved != null && saved.isEnabled) notifications.showExitAlert(saved)
        }
        tracked.remove(id)
        exitCounters.remove(id)
        setZoneState(id, ZoneState.UNKNOWN)
    }

    private fun drop(id: Int) {
        tracked.remove(id)
        exitCounters.remove(id)
        setZoneState(id, ZoneState.UNKNOWN)
        notifications.cancelAlert(id)
    }

    // ---- location updates --------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun setMode(active: Boolean) {
        if (activeMode == active) return
        activeMode = active

        val request = if (active) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.ACTIVE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(GeofenceConstants.ACTIVE_MIN_DISTANCE_M)
                .build()
        } else {
            // alert already sent: relax a little, still watch for the user coming back
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.IDLE_INTERVAL_MS)
                .setMinUpdateDistanceMeters(GeofenceConstants.IDLE_MIN_DISTANCE_M)
                .build()
        }
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    private fun shutdown() {
        fused.removeLocationUpdates(callback)
        activeMode = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- state ---------------------------------------------------------------------------

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
        private const val ACTION_UNTRACK = "com.rohan.raksha.geofence.ACTION_UNTRACK"
        private const val EXTRA_LOCATION_ID = "location_id"
        private const val KEY_TRACKED = "tracked_ids"

        fun track(context: Context, locationId: Int) = send(context, ACTION_TRACK, locationId)
        fun untrack(context: Context, locationId: Int) = send(context, ACTION_UNTRACK, locationId)

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
