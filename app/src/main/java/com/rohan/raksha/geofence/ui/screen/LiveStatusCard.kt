package com.rohan.raksha.geofence.ui.screen

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.geofence.GeofenceManager
import com.rohan.raksha.geofence.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows what the tracking service is doing right now (for testing / debugging). */
@Composable
fun LiveStatusCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(GeofenceConstants.PREFS, Context.MODE_PRIVATE) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    val now = System.currentTimeMillis()
    val statusTime = remember(tick) { prefs.getLong("status_time", 0L) }
    val heartbeat = remember(tick) { prefs.getLong("svc_heartbeat", 0L) }
    val summary = remember(tick) { prefs.getString("status_summary", "") ?: "" }
    val error = remember(tick) { prefs.getString("svc_error", "") ?: "" }
    val source = remember(tick) { prefs.getString("svc_source", "") ?: "" }
    val alertInfo = remember(tick) { prefs.getString("alert_info", "") ?: "" }
    val helper = remember { NotificationHelper(context.applicationContext) }
    val alertBlocked = remember(tick) { helper.alertBlockedReason() }

    val fixAge = if (statusTime == 0L) -1L else (now - statusTime) / 1000
    val beatAge = if (heartbeat == 0L) -1L else (now - heartbeat) / 1000

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Live status",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val headline = when {
                error.isNotEmpty() -> "Problem: $error"
                beatAge < 0 -> "Monitoring service is not running. Add a safe zone or tap Restart monitoring."
                beatAge > 60 -> "Monitoring service stopped responding ${beatAge}s ago. Tap Restart monitoring " +
                    "and check battery settings (set to Unrestricted)."
                fixAge < 0 -> "Service is running, waiting for the first GPS fix. Go outside or near a window."
                fixAge > 120 -> "Last fix was ${fixAge}s ago. You are probably far from every safe zone " +
                    "(low-power mode) or GPS has no signal."
                else -> "Monitoring is running. Last fix ${fixAge}s ago."
            }
            Text(headline, color = MaterialTheme.colorScheme.onSurface)
            if (source.isNotEmpty()) {
                Text(
                    "Source: $source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "State must show INSIDE before leaving the circle. Then the alert fires after 2 fixes outside.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (alertBlocked != null) "Alerts blocked: $alertBlocked"
                else if (alertInfo.isNotEmpty()) "Last alert: $alertInfo"
                else "Alerts: notifications are allowed",
                style = MaterialTheme.typography.bodySmall,
                color = if (alertBlocked != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alertBlocked != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) { }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open notification settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val list = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).savedLocationDao().getAll().first()
                        }
                        prefs.edit().remove("svc_error").apply()
                        GeofenceManager(context).sync(list)
                        Toast.makeText(context, "Monitoring restarted", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restart monitoring")
            }
        }
    }
}
