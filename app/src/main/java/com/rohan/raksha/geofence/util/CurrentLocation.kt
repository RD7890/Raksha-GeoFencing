package com.rohan.raksha.geofence.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One fresh, high-accuracy fix of where the phone is right now. Used by "Set Home to my location".
 * Tries Google's fused provider first, then the plain Android providers, so it also works on
 * phones without Google Play Services. A cached fix is only accepted if it is under 2 minutes old.
 */
object CurrentLocation {
    private const val MAX_CACHED_AGE_MS = 120_000L

    @SuppressLint("MissingPermission")
    suspend fun get(context: Context, timeoutMs: Long = 12_000L): Location? {
        val app = context.applicationContext
        if (!PermissionUtil.hasLocationPermissions(app)) return null

        val gms = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }

        if (gms) {
            val client = LocationServices.getFusedLocationProviderClient(app)
            val fresh = withTimeoutOrNull(timeoutMs) {
                try {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                } catch (e: Exception) {
                    null
                }
            }
            if (fresh != null) return fresh
            val last = try { client.lastLocation.await() } catch (e: Exception) { null }
            if (last != null && isRecent(last)) return last
        }
        return platformFix(app, timeoutMs)
    }

    private fun isRecent(location: Location) =
        System.currentTimeMillis() - location.time < MAX_CACHED_AGE_MS

    @SuppressLint("MissingPermission")
    private suspend fun platformFix(context: Context, timeoutMs: Long): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .firstOrNull { lm.isProviderEnabled(it) }
                if (provider != null) {
                    val fresh = withTimeoutOrNull(timeoutMs) {
                        suspendCancellableCoroutine<Location?> { cont ->
                            val signal = CancellationSignal()
                            cont.invokeOnCancellation { signal.cancel() }
                            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }
                    }
                    if (fresh != null) return fresh
                }
            }
            return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .filter { isRecent(it) }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        } catch (e: SecurityException) {
            return null
        }
    }
}
