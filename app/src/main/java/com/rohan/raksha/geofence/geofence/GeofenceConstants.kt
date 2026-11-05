package com.rohan.raksha.geofence.geofence

import kotlin.math.max

object GeofenceConstants {
    // Safe zone radius limits (GPS is not reliable below ~30 m)
    const val MIN_RADIUS_METERS = 30f
    const val MAX_RADIUS_METERS = 500f
    const val DEFAULT_RADIUS_METERS = 50f

    // Outer ring registered with Play Services: only used to wake the app up
    const val OUTER_RING_EXTRA_METERS = 150f
    const val OUTER_RING_MIN_METERS = 200f
    const val MAX_GEOFENCES = 100

    // Inner tracking (foreground service)
    const val MAX_ACCURACY_METERS = 30f          // ignore fixes worse than this
    const val ACCURACY_MARGIN_CAP_METERS = 10f   // extra slack added to radius for exit
    const val CONFIRM_EXIT_FIXES = 2             // consecutive "outside" fixes needed
    const val ACTIVE_INTERVAL_MS = 3_000L
    const val ACTIVE_MIN_DISTANCE_M = 3f
    const val IDLE_INTERVAL_MS = 10_000L         // after alert has fired
    const val IDLE_MIN_DISTANCE_M = 10f

    const val NOTIFICATION_CHANNEL_SERVICE = "raksha_service"
    const val NOTIFICATION_CHANNEL_ALERT = "raksha_alert"
    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val ALERT_NOTIFICATION_BASE_ID = 2000
    const val ALERT_AUTO_CANCEL_MS = 120_000L

    const val PREFS = "geofence_prefs"

    fun outerRingFor(radiusMeters: Float): Float =
        max(OUTER_RING_MIN_METERS, radiusMeters + OUTER_RING_EXTRA_METERS)
}
