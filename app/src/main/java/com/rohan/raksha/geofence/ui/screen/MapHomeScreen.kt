package com.rohan.raksha.geofence.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.ui.components.FloatingBarSpace
import com.rohan.raksha.geofence.ui.components.FloatingPanel
import com.rohan.raksha.geofence.ui.components.FloatingTopBar
import com.rohan.raksha.geofence.ui.components.IconBadge
import com.rohan.raksha.geofence.ui.components.MapFab
import com.rohan.raksha.geofence.ui.components.OneLineText
import com.rohan.raksha.geofence.ui.components.Pill
import com.rohan.raksha.geofence.ui.components.PrimaryButton
import com.rohan.raksha.geofence.ui.components.ProtectionInfo
import com.rohan.raksha.geofence.ui.components.SecondaryButton
import com.rohan.raksha.geofence.ui.components.SmallButton
import com.rohan.raksha.geofence.ui.components.rememberProtection
import com.rohan.raksha.geofence.ui.map.RakshaMap
import com.rohan.raksha.geofence.ui.map.SafeZoneLayer
import com.rohan.raksha.geofence.ui.theme.Raksha
import com.rohan.raksha.geofence.ui.viewmodel.HomeResult
import com.rohan.raksha.geofence.ui.viewmodel.MainViewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Home tab: a full-screen map with floating top bar, floating bottom navigation and, above it,
 * the one-tap "Set Home to my location" card.
 */
@Composable
fun MapHomeScreen(viewModel: MainViewModel, onAddZone: () -> Unit) {
    val c = Raksha.colors
    val context = LocalContext.current
    val locations by viewModel.savedLocations.collectAsState()
    val focusId by viewModel.focusZoneId.collectAsState()
    val protection = rememberProtection(locations)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var settingHome by remember { mutableStateOf(false) }
    var confirmMove by remember { mutableStateOf(false) }

    val home = locations.firstOrNull { it.name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true) }

    // Draw every zone as a red circle whenever the list or the style changes
    LaunchedEffect(locations, style) {
        val s = style ?: return@LaunchedEffect
        SafeZoneLayer.update(s, locations.filter { it.isEnabled }.map {
            LatLng(it.latitude, it.longitude) to it.exitRadiusMeters.toDouble()
        })
    }

    // The Zones tab asked us to fly to a zone
    LaunchedEffect(focusId, map) {
        val id = focusId ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val zone = locations.firstOrNull { it.id == id }
        if (zone != null) {
            m.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(zone.latitude, zone.longitude), 18.0), 900)
        }
        viewModel.consumeFocus()
    }

    fun setHome() {
        if (settingHome) return
        settingHome = true
        viewModel.setHomeHere { result ->
            settingHome = false
            when (result) {
                is HomeResult.Saved -> {
                    map?.let { m ->
                        m.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
                        m.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(result.latitude, result.longitude), 18.0), 900
                        )
                    }
                    val verb = if (result.movedExisting) "Home moved here" else "Home set here"
                    val acc = if (result.accuracyMeters > 0) " (GPS ±${result.accuracyMeters.toInt()} m)" else ""
                    val weak = if (result.accuracyMeters > GeofenceConstants.HOME_RADIUS_METERS)
                        "\nGPS is weak. Step outside for a more exact Home." else ""
                    Toast.makeText(context, "$verb · ${GeofenceConstants.HOME_RADIUS_METERS.toInt()} m safe zone$acc$weak", Toast.LENGTH_LONG).show()
                }
                HomeResult.NoFix -> Toast.makeText(
                    context, "Couldn't get your location. Turn on GPS and try again.", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        RakshaMap(
            modifier = Modifier.fillMaxSize(),
            followUser = true,
            followZoom = 17.0,
            onReady = { m, s -> map = m; style = s }
        )

        // ── Floating top bar ────────────────────────────────────────────────
        FloatingTopBar(
            title = "ରକ୍ଷା",
            subtitle = when (protection.level) {
                ProtectionInfo.Level.OFF -> if (locations.isEmpty()) "Set up your safe zone" else "Safe zones paused"
                ProtectionInfo.Level.STARTING -> "Starting protection…"
                ProtectionInfo.Level.ACTIVE -> {
                    val n = locations.count { it.isEnabled }
                    "Watching $n safe zone${if (n == 1) "" else "s"}"
                }
                ProtectionInfo.Level.ERROR -> "Needs attention"
            },
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                when (protection.level) {
                    ProtectionInfo.Level.ACTIVE -> Pill("Protected", c.mint, c.mintSoft, icon = R.drawable.ph_shield_check_fill)
                    ProtectionInfo.Level.STARTING -> Pill("Starting", c.amber, c.amberSoft, icon = R.drawable.ph_circle_notch)
                    ProtectionInfo.Level.ERROR -> Pill("Fix needed", c.danger, c.roseSoft, icon = R.drawable.ph_warning_circle_fill)
                    ProtectionInfo.Level.OFF -> Pill("Off", c.textMuted, c.card, icon = R.drawable.ph_shield_slash_fill)
                }
                Spacer(Modifier.width(8.dp))
            }
        )

        // ── Bottom stack: map buttons + Home card (sits above the floating nav) ──
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = navInset + FloatingBarSpace)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapFab(R.drawable.ph_navigation_arrow_fill, "Follow my location", onClick = {
                    val component = map?.locationComponent
                    if (component != null && component.isLocationComponentActivated) {
                        component.cameraMode = CameraMode.TRACKING
                        component.zoomWhileTracking(17.0)
                    }
                })
                Spacer(Modifier.width(12.dp))
                MapFab(R.drawable.ph_plus_bold, "Add safe zone", onClick = onAddZone, filled = true)
            }

            FloatingPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                if (home == null) {
                    SetHomeCard(loading = settingHome, onSetHome = ::setHome, onChoose = onAddZone)
                } else {
                    HomeSummary(
                        zone = home,
                        state = protection.zoneStates[home.id],
                        loading = settingHome,
                        onMove = { confirmMove = true }
                    )
                }
            }
        }
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            containerColor = Color.White,
            title = { Text("Move Home here?", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Your Home safe zone will move to where you are right now and keep its ${GeofenceConstants.HOME_RADIUS_METERS.toInt()} m radius.") },
            confirmButton = {
                TextButton(onClick = { confirmMove = false; setHome() }) {
                    Text("Move Home", color = c.rose, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMove = false }) { Text("Cancel", color = c.textMuted) }
            }
        )
    }
}

@Composable
private fun SetHomeCard(loading: Boolean, onSetHome: () -> Unit, onChoose: () -> Unit) {
    val c = Raksha.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(R.drawable.ic_home_fill, tint = c.rose, background = c.roseSoft, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Mark your home", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Stand where you want protection. One tap sets a ${GeofenceConstants.HOME_RADIUS_METERS.toInt()} m safe zone around you.",
                color = c.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        text = "Set Home to my location",
        onClick = onSetHome,
        icon = R.drawable.ph_crosshair,
        loading = loading
    )
    Spacer(Modifier.height(8.dp))
    SecondaryButton(text = "Choose another place", onClick = onChoose, icon = R.drawable.ph_map_pin_line, height = 46.dp)
}

@Composable
private fun HomeSummary(zone: SavedLocation, state: String?, loading: Boolean, onMove: () -> Unit) {
    val c = Raksha.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(R.drawable.ic_home_fill, tint = c.rose, background = c.roseSoft, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            OneLineText(zone.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${zone.exitRadiusMeters.toInt()} m safe zone", color = c.textMuted, fontSize = 12.5.sp)
                Spacer(Modifier.width(8.dp))
                when {
                    !zone.isEnabled -> Pill("Paused", c.textMuted, c.card)
                    state == "INSIDE" -> Pill("Inside", c.mint, c.mintSoft)
                    state == "OUTSIDE" -> Pill("Outside", c.danger, c.roseSoft)
                    else -> Pill("Checking", c.amber, c.amberSoft)
                }
            }
        }
        SmallButton(
            text = if (loading) "Locating…" else "Move here",
            onClick = { if (!loading) onMove() },
            icon = R.drawable.ph_crosshair,
            filled = false
        )
    }
}
