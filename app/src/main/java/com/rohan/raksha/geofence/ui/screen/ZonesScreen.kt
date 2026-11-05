package com.rohan.raksha.geofence.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.ui.components.FloatingBarSpace
import com.rohan.raksha.geofence.ui.components.FloatingTopBar
import com.rohan.raksha.geofence.ui.components.GlassCard
import com.rohan.raksha.geofence.ui.components.IconBadge
import com.rohan.raksha.geofence.ui.components.IconChip
import com.rohan.raksha.geofence.ui.components.MapFab
import com.rohan.raksha.geofence.ui.components.OneLineText
import com.rohan.raksha.geofence.ui.components.Pill
import com.rohan.raksha.geofence.ui.components.PrimaryButton
import com.rohan.raksha.geofence.ui.components.SecondaryButton
import com.rohan.raksha.geofence.ui.components.rakshaSwitchColors
import com.rohan.raksha.geofence.ui.components.rememberProtection
import com.rohan.raksha.geofence.ui.theme.Raksha
import com.rohan.raksha.geofence.ui.viewmodel.HomeResult
import com.rohan.raksha.geofence.ui.viewmodel.MainViewModel

/** Zones tab: every safe zone as a clean card, with a floating "add" button. */
@Composable
fun ZonesScreen(viewModel: MainViewModel, onAddZone: () -> Unit, onOpenOnMap: () -> Unit) {
    val c = Raksha.colors
    val context = LocalContext.current
    val zones by viewModel.savedLocations.collectAsState()
    val protection = rememberProtection(zones)
    var toDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var settingHome by remember { mutableStateOf(false) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FloatingBarSpace + 4.dp
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = navInset + FloatingBarSpace + 88.dp

    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (zones.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(top = top, start = 32.dp, end = 32.dp, bottom = bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconBadge(R.drawable.ph_shield_check_fill, tint = c.rose, background = c.roseSoft, size = 84.dp,
                    iconSize = 42.dp, shape = androidx.compose.foundation.shape.CircleShape)
                Spacer(Modifier.height(20.dp))
                Text("No safe zones yet", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stand at home and tap once. Raksha alerts you when you leave the circle.",
                    color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    text = "Set Home to my location",
                    icon = R.drawable.ph_crosshair,
                    loading = settingHome,
                    onClick = {
                        settingHome = true
                        viewModel.setHomeHere { r ->
                            settingHome = false
                            val msg = when (r) {
                                is HomeResult.Saved -> "Home set · ${GeofenceConstants.HOME_RADIUS_METERS.toInt()} m safe zone"
                                HomeResult.NoFix -> "Couldn't get your location. Turn on GPS and try again."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Choose on the map", onClick = onAddZone, icon = R.drawable.ph_map_pin_line)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(zones, key = { it.id }) { zone ->
                    ZoneCard(
                        zone = zone,
                        state = protection.zoneStates[zone.id],
                        onOpen = { viewModel.focusZone(zone.id); onOpenOnMap() },
                        onToggle = { viewModel.toggleEnabled(zone) },
                        onDelete = { toDelete = zone }
                    )
                }
            }
        }

        FloatingTopBar(
            title = "Safe zones",
            subtitle = when (zones.size) {
                0 -> "Nothing protected yet"
                1 -> "1 place protected"
                else -> "${zones.size} places protected"
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (zones.isNotEmpty()) {
            MapFab(
                R.drawable.ph_plus_bold, "Add safe zone", onClick = onAddZone, filled = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = navInset + FloatingBarSpace + 16.dp)
            )
        }
    }

    toDelete?.let { zone ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Color.White,
            title = { Text("Delete \"${zone.name}\"?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("You will no longer get an alert when you leave this place.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLocation(zone); toDelete = null }) {
                    Text("Delete", color = c.danger, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel", color = c.textMuted) } }
        )
    }
}

@Composable
private fun ZoneCard(
    zone: SavedLocation,
    state: String?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Raksha.colors
    val isHome = zone.name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true)
    GlassCard(onClick = onOpen, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (isHome) R.drawable.ic_home_fill else R.drawable.ph_map_pin_fill,
                tint = if (zone.isEnabled) c.rose else c.textFaint,
                background = if (zone.isEnabled) c.roseSoft else c.cardHigh,
                size = 48.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                OneLineText(zone.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${zone.exitRadiusMeters.toInt()} m", color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    when {
                        !zone.isEnabled -> Pill("Paused", c.textMuted, c.cardHigh)
                        state == "INSIDE" -> Pill("Inside", c.mint, c.mintSoft)
                        state == "OUTSIDE" -> Pill("Outside", c.danger, c.roseSoft)
                        else -> Pill("Checking", c.amber, c.amberSoft)
                    }
                }
            }
            Switch(checked = zone.isEnabled, onCheckedChange = { onToggle() }, colors = rakshaSwitchColors())
            Spacer(Modifier.width(8.dp))
            IconChip(R.drawable.ph_trash, "Delete ${zone.name}", onDelete, tint = c.danger, size = 40.dp)
        }
    }
}

