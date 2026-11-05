package com.rohan.raksha.geofence.ui.screen

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.geofence.GeofenceManager
import com.rohan.raksha.geofence.notifications.NotificationHelper
import com.rohan.raksha.geofence.ui.components.GlassCard
import com.rohan.raksha.geofence.ui.components.IconBadge
import com.rohan.raksha.geofence.ui.components.SecondaryButton
import com.rohan.raksha.geofence.ui.theme.Raksha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows what the tracking service is doing right now (for testing / debugging). */
@Composable
fun LiveStatusCard() {
    val c = Raksha.colors
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

    val good = error.isEmpty() && beatAge in 0..60
    val headline = when {
        error.isNotEmpty() -> "Problem: $error"
        beatAge < 0 -> "Monitoring is not running. Add a safe zone or tap Restart."
        beatAge > 60 -> "Monitoring stopped responding ${beatAge}s ago. Tap Restart and set battery to Unrestricted."
        fixAge < 0 -> "Running, waiting for the first GPS fix. Go outside or near a window."
        fixAge > 120 -> "Last fix ${fixAge}s ago. You are far from every zone (low-power mode) or GPS has no signal."
        else -> "Monitoring is running. Last fix ${fixAge}s ago."
    }

    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(
                if (good) R.drawable.ph_shield_check_fill else R.drawable.ph_warning_circle_fill,
                tint = if (good) c.mint else c.danger,
                background = if (good) c.mintSoft else c.roseSoft
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                if (source.isNotEmpty()) {
                    Text("Source: $source", color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(summary, color = c.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "A zone must show INSIDE before you leave it. The alert fires after 2 fixes outside the circle.",
            color = c.textFaint, fontSize = 11.5.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                alertBlocked != null -> "Alerts blocked: $alertBlocked"
                alertInfo.isNotEmpty() -> "Last alert: $alertInfo"
                else -> "Alerts: notifications are allowed"
            },
            color = if (alertBlocked != null) c.danger else c.textMuted,
            fontSize = 12.sp,
            fontWeight = if (alertBlocked != null) FontWeight.Bold else FontWeight.Normal
        )
        if (alertBlocked != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Open notification settings",
                height = 46.dp,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) { }
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            "Restart monitoring",
            icon = R.drawable.ph_arrow_clockwise,
            height = 46.dp,
            onClick = {
                scope.launch {
                    val list = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context).savedLocationDao().getAll().first()
                    }
                    prefs.edit().remove("svc_error").apply()
                    GeofenceManager(context).sync(list)
                    Toast.makeText(context, "Monitoring restarted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
