package com.rohan.raksha.geofence.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Runs whenever at least one safe zone is enabled (started by the app, by boot, and as a
 * backup by the outer-ring geofence). Streams GPS fixes, compares them with the exact
 * radius and raises the full-screen alert when the user is confirmed to be outside.
 * Battery: high accuracy only while the user is near a zone, balanced power when far away.
 *
 * Reliability measures:
 *  - no distance filter while near a zone, so fixes (and the Live status card) keep flowing
 *  - a watchdog restarts the location request if no fix arrives, and switches between Google's
 *    fused provider and the plain Android provider if one of them keeps stalling
 *  - works on phones without Google Play Services (plain Android location provider)
 *  - every failure is written to preferences so the Live status card can show it
 */
class TrackingService : Service() {

    private enum class ZoneState { UNKNOWN, INSIDE, OUTSIDE }

    // ordinal order matters: the highest mode of all zones wins
    private enum class Mode { FAR, ALERTED, ACTIVE }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private val tracked = linkedSetOf<Int>()
    private val exitCounters = mutableMapOf<Int, Int>()

    @Volatile private var currentMode: Mode? = null
    @Volatile private var lastFixElapsed = 0L
    @Volatile private var requestFailed = false
    private var stallCount = 0
    private var useFused = true
    private var gmsAvailable = true
    private var watchdog: Job? = null

    private lateinit var notifications: NotificationHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleFix(location)
        }
    }

    // Plain Android provider. All four methods are implemented because on Android 8-10 the
    // platform does not supply default implementations for the last three.
    private val platformListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleFix(location)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationHelper(this)
        prefs = getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE)
        fused = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gmsAvailable = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
        useFused = gmsAvailable
        if (!gmsAvailable) Log.w(TAG, "Google Play Services unavailable, using the Android location provider")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called quickly after startForegroundService()
        if (!startAsForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

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
                } else {
                    heartbeat()
                    startWatchdog()
                    if (currentMode == null) {
                        setMode(Mode.ACTIVE) // high accuracy until the first fix tells us how far we are
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground(): Boolean {
        val notification = notifications.createTrackingNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    GeofenceConstants.NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(GeofenceConstants.NOTIFICATION_ID_FOREGROUND, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            setError("Android refused to start the tracking service (${e.javaClass.simpleName}). " +
                "Open the app once and allow location \"all the time\".")
            false
        }
    }

    // ---- location handling -------------------------------------------------------------

    /** Entry point for every fix, from any provider and any thread. */
    private fun handleFix(location: Location) {
        lastFixElapsed = SystemClock.elapsedRealtime()
        requestFailed = false
        stallCount = 0
        scope.launch { mutex.withLock { process(location) } }
    }

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
                // A reasonable fix whose error circle touches the safe zone (re)arms it. Taking the
                // GPS error into account matters for small zones / indoors, where the reported
                // position often drifts outside the circle and the zone would never arm.
                val slack = min(accuracy, GeofenceConstants.ARM_ACCURACY_SLACK_METERS)
                if (accuracy <= GeofenceConstants.ARM_MAX_ACCURACY_METERS && distance - slack <= radius) {
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

    private fun setMode(mode: Mode) {
        if (currentMode == mode && !requestFailed) return
        currentMode = mode
        Log.d(TAG, "Location mode -> $mode (${if (useFused) "fused" else "platform"})")
        requestUpdates(mode)
    }

    private fun stopUpdates() {
        try { fused.removeLocationUpdates(callback) } catch (e: Exception) { Log.w(TAG, "remove fused", e) }
        try { locationManager.removeUpdates(platformListener) } catch (e: Exception) { Log.w(TAG, "remove platform", e) }
    }

    private fun requestUpdates(mode: Mode) {
        stopUpdates()
        lastFixElapsed = SystemClock.elapsedRealtime()
        requestFailed = false
        val ok = if (useFused) requestFused(mode) else requestPlatform(mode)
        if (!ok) requestFailed = true
        prefs.edit().putString("svc_source", if (useFused) "Google fused location" else "Android GPS/network").apply()
    }

    @SuppressLint("MissingPermission")
    private fun requestFused(mode: Mode): Boolean {
        val request = when (mode) {
            Mode.ACTIVE ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.ACTIVE_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(1_000L)
                    .setMinUpdateDistanceMeters(GeofenceConstants.ACTIVE_MIN_DISTANCE_M)
                    .setWaitForAccurateLocation(false)
                    .build()
            Mode.ALERTED ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GeofenceConstants.IDLE_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GeofenceConstants.IDLE_MIN_DISTANCE_M)
                    .setWaitForAccurateLocation(false)
                    .build()
            Mode.FAR ->
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, GeofenceConstants.FAR_INTERVAL_MS)
                    .setMinUpdateDistanceMeters(GeofenceConstants.FAR_MIN_DISTANCE_M)
                    .build()
        }
        return try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    Log.e(TAG, "Fused location request failed", e)
                    setError("Location request failed: ${e.message}")
                    requestFailed = true
                }
            // Ask for one fix right now so the first status does not wait for the next interval
            if (mode != Mode.FAR) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc -> if (loc != null) handleFix(loc) }
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            setError("Location permission missing. Allow location \"all the time\" for Raksha.")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPlatform(mode: Mode): Boolean {
        val precise = mode != Mode.FAR
        val interval = when (mode) {
            Mode.ACTIVE -> GeofenceConstants.ACTIVE_INTERVAL_MS
            Mode.ALERTED -> GeofenceConstants.IDLE_INTERVAL_MS
            Mode.FAR -> GeofenceConstants.FAR_INTERVAL_MS
        }
        val distance = if (precise) 0f else GeofenceConstants.FAR_MIN_DISTANCE_M
        val providers = if (precise) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        return try {
            var registered = false
            for (provider in providers) {
                if (!locationManager.isProviderEnabled(provider)) continue
                locationManager.requestLocationUpdates(
                    provider, interval, distance, platformListener, Looper.getMainLooper()
                )
                registered = true
                if (!precise) break // one provider is enough far away
            }
            if (!registered) setError("Location is switched off. Turn on GPS / location services.")
            registered
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            setError("Location permission missing. Allow location \"all the time\" for Raksha.")
            false
        }
    }

    // ---- watchdog ------------------------------------------------------------------------

    /** Heartbeat + self-repair: restarts the location request when fixes stop arriving. */
    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (isActive) {
                delay(GeofenceConstants.WATCHDOG_INTERVAL_MS)
                mutex.withLock {
                    if (tracked.isEmpty()) return@withLock
                    heartbeat()
                    val mode = currentMode ?: Mode.ACTIVE
                    val silentFor = SystemClock.elapsedRealtime() - lastFixElapsed
                    // FAR mode only reports after real movement, so silence is normal there
                    val stalled = mode != Mode.FAR && silentFor > GeofenceConstants.STALL_TIMEOUT_MS
                    if (stalled || requestFailed) {
                        stallCount++
                        Log.w(TAG, "No location for ${silentFor / 1000}s (stall #$stallCount), restarting updates")
                        if (stallCount >= 3 && gmsAvailable) {
                            // this provider keeps failing: try the other one
                            useFused = !useFused
                            stallCount = 0
                            Log.w(TAG, "Switching location source, fused=$useFused")
                        }
                        currentMode = mode
                        requestUpdates(mode)
                    }
                }
            }
        }
    }

    private fun shutdown() {
        watchdog?.cancel()
        watchdog = null
        stopUpdates()
        currentMode = null
        prefs.edit()
            .remove("status_summary").remove("status_time")
            .remove("svc_heartbeat").remove("svc_error").remove("svc_source")
            .apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- state ---------------------------------------------------------------------------

    private fun heartbeat() {
        prefs.edit().putLong("svc_heartbeat", System.currentTimeMillis()).apply()
    }

    private fun setError(message: String) {
        prefs.edit().putString("svc_error", message).apply()
    }

    private fun recordStatus(lines: List<String>) {
        prefs.edit()
            .putString("status_summary", lines.joinToString("\n"))
            .putLong("status_time", System.currentTimeMillis())
            .putLong("svc_heartbeat", System.currentTimeMillis())
            .remove("svc_error") // a fix arrived, so whatever was wrong is fixed
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
        stopUpdates()
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
                context.getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE)
                    .edit().putString("svc_error", "Could not start tracking service: ${e.javaClass.simpleName}").apply()
            }
        }
    }
}
