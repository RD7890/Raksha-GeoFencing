package com.rohan.raksha.geofence.geofence

import kotlin.math.max

object GeofenceConstants {
    // Safe zone radius limits (GPS is not reliable below ~30 m)
    const val MIN_RADIUS_METERS = 30f
    const val MAX_RADIUS_METERS = 500f
    const val DEFAULT_RADIUS_METERS = 50f

    // One-tap "Set Home to my location"
    const val HOME_NAME = "Home"
    const val HOME_RADIUS_METERS = 30f

    // Outer ring registered with Play Services: only used to wake the app up
    const val OUTER_RING_EXTRA_METERS = 150f
    const val OUTER_RING_MIN_METERS = 200f
    const val MAX_GEOFENCES = 100

    // Inner tracking (foreground service)
    const val MAX_ACCURACY_METERS = 30f          // ignore fixes worse than this
    const val ACCURACY_MARGIN_CAP_METERS = 10f   // extra slack added to radius for exit
    const val CONFIRM_EXIT_FIXES = 2             // consecutive "outside" fixes needed
    const val ACTIVE_INTERVAL_MS = 3_000L
    const val ACTIVE_MIN_DISTANCE_M = 0f         // no distance filter: a fix every interval, even standing still
    const val IDLE_INTERVAL_MS = 10_000L         // after alert has fired
    const val IDLE_MIN_DISTANCE_M = 0f
    const val FAR_INTERVAL_MS = 30_000L          // far from every zone: save battery
    const val FAR_MIN_DISTANCE_M = 20f
    const val ARM_MAX_ACCURACY_METERS = 60f      // fix quality needed to mark "inside"
    const val ARM_ACCURACY_SLACK_METERS = 30f    // GPS error tolerated when arming a zone

    // Watchdog: restarts location updates if the stream silently stalls
    const val WATCHDOG_INTERVAL_MS = 15_000L
    const val STALL_TIMEOUT_MS = 30_000L

    const val NOTIFICATION_CHANNEL_SERVICE = "raksha_service"
    // v2: Android never lets an app change a channel after creation, so a channel created by an
    // older build keeps its old (silent / low-importance) settings. A new id forces fresh settings.
    const val NOTIFICATION_CHANNEL_ALERT = "raksha_alert_v2"
    const val NOTIFICATION_CHANNEL_ALERT_LEGACY = "raksha_alert"
    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val ALERT_NOTIFICATION_BASE_ID = 2000
    const val ALERT_AUTO_CANCEL_MS = 120_000L

    const val PREFS = "geofence_prefs"

    fun outerRingFor(radiusMeters: Float): Float =
        max(OUTER_RING_MIN_METERS, radiusMeters + OUTER_RING_EXTRA_METERS)
}
