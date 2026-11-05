package com.rohan.raksha.geofence.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rohan.raksha.geofence.service.TrackingService

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e("GeofenceReceiver", "Geofencing error: ${event.errorCode}")
            return
        }
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toIntOrNull() } ?: return

        when (event.geofenceTransition) {
            // Entered the outer ring -> start precise tracking
            Geofence.GEOFENCE_TRANSITION_ENTER -> ids.forEach { TrackingService.track(context, it) }
            // Left the outer ring -> definitely out of the safe zone (backup trigger)
            Geofence.GEOFENCE_TRANSITION_EXIT -> ids.forEach { TrackingService.outerExit(context, it) }
        }
    }
}
