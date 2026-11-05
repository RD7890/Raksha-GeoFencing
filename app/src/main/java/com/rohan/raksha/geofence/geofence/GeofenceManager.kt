package com.rohan.raksha.geofence.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.service.TrackingService

/**
 * Registers one "outer ring" geofence per enabled safe zone. The ring is only a wake-up
 * trigger: ENTER starts [com.rohan.raksha.geofence.service.TrackingService], which does the
 * precise safe-zone check with GPS.
 */
class GeofenceManager(context: Context) {
    private val context = context.applicationContext
    private val client: GeofencingClient = LocationServices.getGeofencingClient(this.context)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** Replaces everything that is registered with the enabled [locations]. */
    @SuppressLint("MissingPermission")
    fun sync(locations: List<SavedLocation>) {
        if (!hasPermissions()) {
            Log.w(TAG, "Missing location permissions, geofences not registered")
            return
        }
        val enabled = locations.filter { it.isEnabled }.take(GeofenceConstants.MAX_GEOFENCES)
        val pi = pendingIntent()

        // Start precise tracking right away; do not wait for the (slow) geofence ENTER event
        enabled.forEach { TrackingService.track(context, it.id) }

        client.removeGeofences(pi).addOnCompleteListener {
            if (enabled.isEmpty()) return@addOnCompleteListener

            val geofences = enabled.map { loc ->
                Geofence.Builder()
                    .setRequestId(loc.id.toString())
                    .setCircularRegion(
                        loc.latitude, loc.longitude,
                        GeofenceConstants.outerRingFor(loc.exitRadiusMeters)
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                // fires ENTER immediately if the phone is already inside the ring
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()

            try {
                client.addGeofences(request, pi)
                    .addOnSuccessListener { Log.d(TAG, "Registered ${geofences.size} geofence(s)") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to register geofences", e) }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while registering geofences", e)
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && bg
    }

    private companion object {
        const val TAG = "GeofenceManager"
    }
}
