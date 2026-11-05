package com.rohan.raksha.geofence.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.ui.components.FloatingBarSpace
import com.rohan.raksha.geofence.ui.components.FloatingTopBar
import com.rohan.raksha.geofence.ui.components.GlassCard
import com.rohan.raksha.geofence.ui.components.IconBadge
import com.rohan.raksha.geofence.ui.components.SectionTitle
import com.rohan.raksha.geofence.ui.components.SmallButton
import com.rohan.raksha.geofence.ui.theme.Raksha
import com.rohan.raksha.geofence.util.MiuiHelper

/** Settings tab: alert setup, live status, background hints. */
@Composable
fun SettingsScreen() {
    val c = Raksha.colors
    val context = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FloatingBarSpace + 4.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBarSpace + 16.dp

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("ALERTS")
            AlertSetupCard()

            SectionTitle("LIVE STATUS", Modifier.padding(top = 8.dp))
            LiveStatusCard()

            SectionTitle("BACKGROUND", Modifier.padding(top = 8.dp))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        R.drawable.ph_battery_warning_fill,
                        tint = if (MiuiHelper.isMiui()) c.amber else c.mint,
                        background = if (MiuiHelper.isMiui()) c.amberSoft else c.mintSoft
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (MiuiHelper.isMiui()) "Xiaomi / HyperOS detected" else "No special setup needed",
                            color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (MiuiHelper.isMiui())
                                "Turn on Autostart and set Battery to \"No restrictions\" so protection keeps running."
                            else "Your phone doesn't need any extra background settings.",
                            color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp
                        )
                    }
                }
                if (MiuiHelper.isMiui()) {
                    Spacer(Modifier.height(12.dp))
                    SmallButton("Open Autostart settings", onClick = {
                        try { context.startActivity(MiuiHelper.getAutostartIntent()) } catch (e: Exception) { }
                    })
                }
            }

            SectionTitle("ABOUT", Modifier.padding(top = 8.dp))
            GlassCard {
                Text("Raksha GeoFencing v1.4", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Leave your safe zone and Raksha raises the alarm.", color = c.textMuted, fontSize = 12.5.sp)
            }
        }

        FloatingTopBar(
            title = "Settings",
            subtitle = "Alerts and background",
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
