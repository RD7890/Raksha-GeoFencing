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
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.notifications.NotificationHelper
import com.rohan.raksha.geofence.ui.components.GlassCard
import com.rohan.raksha.geofence.ui.components.Pill
import com.rohan.raksha.geofence.ui.components.PrimaryButton
import com.rohan.raksha.geofence.ui.components.SmallButton
import com.rohan.raksha.geofence.ui.theme.Raksha
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

    GlassCard {
        SetupRow("Full-screen alert", fullScreenOk, "Allow") {
            try { launcher.launch(FullScreenIntentUtil.settingsIntent(context)) } catch (e: Exception) { }
        }
        Spacer(Modifier.height(14.dp))
        SetupRow("Unrestricted battery", batteryOk, "Allow") {
            try { launcher.launch(BatteryUtil.requestIntent(context)) } catch (e: Exception) { }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Test alert in 10 seconds",
            icon = R.drawable.ph_bell_ringing_fill,
            height = 52.dp,
            onClick = {
                val app = context.applicationContext
                Toast.makeText(app, "Lock your phone now. Alert in 10 seconds.", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    val helper = NotificationHelper(app)
                    val shown = helper.showExitAlert(
                        SavedLocation(id = 0, name = "Test zone", latitude = 0.0, longitude = 0.0)
                    )
                    if (!shown) {
                        Toast.makeText(app, helper.alertBlockedReason() ?: "Alert could not be shown", Toast.LENGTH_LONG).show()
                    }
                }, 10_000L)
            }
        )
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, actionText: String, onAction: () -> Unit) {
    val c = Raksha.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (ok) {
            Pill("On", c.mint, c.mintSoft, icon = R.drawable.ph_check_circle_fill)
        } else {
            SmallButton(actionText, onClick = onAction)
        }
    }
}
