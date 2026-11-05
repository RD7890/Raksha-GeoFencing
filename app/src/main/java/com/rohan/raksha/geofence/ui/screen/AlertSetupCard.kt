package com.rohan.raksha.geofence.ui.screen

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.notifications.NotificationHelper
import com.rohan.raksha.geofence.util.BatteryUtil
import com.rohan.raksha.geofence.util.FullScreenIntentUtil

@Composable
fun AlertSetupCard() {
    val context = LocalContext.current
    var fullScreenOk by remember { mutableStateOf(FullScreenIntentUtil.canUse(context)) }
    var batteryOk by remember { mutableStateOf(BatteryUtil.isUnrestricted(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        fullScreenOk = FullScreenIntentUtil.canUse(context)
        batteryOk = BatteryUtil.isUnrestricted(context)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Safe zone alert setup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            StatusRow("Full-screen alert", fullScreenOk)
            if (!fullScreenOk) {
                OutlinedButton(onClick = {
                    try { launcher.launch(FullScreenIntentUtil.settingsIntent(context)) } catch (e: Exception) { }
                }) { Text("Allow full-screen alerts") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StatusRow("Battery: unrestricted", batteryOk)
            if (!batteryOk) {
                OutlinedButton(onClick = {
                    try { launcher.launch(BatteryUtil.requestIntent(context)) } catch (e: Exception) { }
                }) { Text("Allow background use") }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val app = context.applicationContext
                    Toast.makeText(app, "Lock your phone now. Alert in 10 seconds.", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val shown = NotificationHelper(app).showExitAlert(
                            SavedLocation(id = 0, name = "Test zone", latitude = 0.0, longitude = 0.0)
                        )
                        if (!shown) {
                            Toast.makeText(
                                app,
                                NotificationHelper(app).alertBlockedReason() ?: "Alert could not be shown",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }, 10_000L)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test alert in 10 seconds")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (ok) "OK" else "Not allowed",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}
