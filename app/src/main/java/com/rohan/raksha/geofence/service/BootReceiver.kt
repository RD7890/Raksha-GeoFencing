package com.rohan.raksha.geofence.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.geofence.GeofenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Geofences are cleared on reboot and app update, so register them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val locations = AppDatabase.getInstance(context).savedLocationDao().getAll().first()
                GeofenceManager(context).sync(locations)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to restore geofences", e)
            } finally {
                pending.finish()
            }
        }
    }
}
