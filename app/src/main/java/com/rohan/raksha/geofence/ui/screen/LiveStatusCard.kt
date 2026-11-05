package com.rohan.raksha.geofence.ui.screen

import android.content.Context
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
            delay(2_000)
            tick++
        }
    }

    val statusTime = remember(tick) { prefs.getLong("status_time", 0L) }
    val summary = remember(tick) { prefs.getString("status_summary", "") ?: "" }
    val ageSeconds = if (statusTime == 0L) -1L else (System.currentTimeMillis() - statusTime) / 1000

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
                ageSeconds < 0 -> "No GPS fix received yet. Add a safe zone, keep GPS on and wait a few seconds."
                ageSeconds > 120 -> "Last fix was ${ageSeconds}s ago. Monitoring may be stopped or the phone is far away (low-power mode)."
                else -> "Monitoring is running. Last fix ${ageSeconds}s ago."
            }
            Text(headline, color = MaterialTheme.colorScheme.onSurface)
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

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val list = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(context).savedLocationDao().getAll().first()
                        }
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
