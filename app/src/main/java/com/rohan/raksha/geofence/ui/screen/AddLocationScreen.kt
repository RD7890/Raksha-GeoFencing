package com.rohan.raksha.geofence.ui.screen

import android.graphics.PointF
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import com.rohan.raksha.geofence.R
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.ui.components.FloatingBarSpace
import com.rohan.raksha.geofence.ui.components.FloatingPanel
import com.rohan.raksha.geofence.ui.components.IconChip
import com.rohan.raksha.geofence.ui.components.MapFab
import com.rohan.raksha.geofence.ui.components.PhIcon
import com.rohan.raksha.geofence.ui.components.PrimaryButton
import com.rohan.raksha.geofence.ui.components.SecondaryButton
import com.rohan.raksha.geofence.ui.components.bounceClick
import com.rohan.raksha.geofence.ui.components.floatingShadow
import com.rohan.raksha.geofence.ui.components.rakshaFieldColors
import com.rohan.raksha.geofence.ui.map.RakshaMap
import com.rohan.raksha.geofence.ui.map.SafeZoneLayer
import com.rohan.raksha.geofence.ui.theme.Raksha
import com.rohan.raksha.geofence.ui.viewmodel.MainViewModel
import com.rohan.raksha.geofence.util.CurrentLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

private val PRESET_NAMES = listOf("Home", "Work", "School")
private val QUICK_RADII = listOf(30f, 50f, 100f, 200f)

/**
 * Full-screen "set safe zone" map. The red pin marks the centre of the zone; drag the map under it,
 * search for a place, or tap "My location". Everything else floats on top of the map.
 */
@Composable
fun AddLocationScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val c = Raksha.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val locations by viewModel.savedLocations.collectAsState()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    // name
    var presetName by remember { mutableStateOf(PRESET_NAMES[0]) }   // null-like when custom is chosen
    var customChosen by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(false) }
    val name = if (customChosen) customName.ifBlank { "My place" } else presetName

    // radius (Home defaults to 30 m)
    var radius by remember { mutableStateOf(GeofenceConstants.HOME_RADIUS_METERS) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    // ── layout measurements: the pin sits in the middle of the free map area ─────────────────
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    var panelH by remember { mutableIntStateOf(0) }
    val topInsetPx = with(density) { WindowInsets.statusBars.getTop(this) + FloatingBarSpace.roundToPx() }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBlockPx = panelH + with(density) { (navInset + 12.dp + 12.dp).roundToPx() }
    val pinY = if (boxH == 0) 0f else topInsetPx + (boxH - bottomBlockPx - topInsetPx) / 2f
    val pinX = boxW / 2f
    val pinPoint = rememberUpdatedState(PointF(pinX, pinY))
    val radiusState = rememberUpdatedState(radius)

    fun pinLatLng(): LatLng? = map?.projection?.fromScreenLocation(pinPoint.value)

    fun redrawCircle() {
        val m = map ?: return
        val s = style ?: return
        if (boxH == 0) return
        val center = m.projection.fromScreenLocation(pinPoint.value)
        SafeZoneLayer.update(s, listOf(center to radiusState.value.toDouble()), showCenters = false)
    }

    // Keep the map's logical viewport equal to the free area, so "centre of the map" == pin position
    LaunchedEffect(map, boxH, panelH) {
        val m = map ?: return@LaunchedEffect
        if (boxH == 0 || panelH == 0) return@LaunchedEffect
        m.setPadding(0, topInsetPx, 0, bottomBlockPx)
        redrawCircle()
    }
    LaunchedEffect(map, style) {
        val m = map ?: return@LaunchedEffect
        m.addOnCameraMoveListener { redrawCircle() }
        redrawCircle()
    }
    LaunchedEffect(radius) { redrawCircle() }

    fun flyTo(target: LatLng) {
        val m = map ?: return
        m.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 18.0), 900)
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { geocode(q) }
            searching = false
            if (found != null) flyTo(found)
            else Toast.makeText(context, "Couldn't find \"$q\"", Toast.LENGTH_SHORT).show()
        }
    }

    fun goToMyLocation() {
        if (locating) return
        locating = true
        scope.launch {
            val fix = CurrentLocation.get(context)
            locating = false
            if (fix != null) flyTo(LatLng(fix.latitude, fix.longitude))
            else Toast.makeText(context, "Couldn't get your location. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxW = it.width; boxH = it.height }
    ) {
        RakshaMap(
            modifier = Modifier.fillMaxSize(),
            followUser = true,
            followZoom = 18.0,
            onReady = { m, s -> map = m; style = s }
        )

        // ── Pin: its tip is exactly the centre of the safe zone ──────────────────────────────
        if (boxH > 0) {
            PhIcon(
                R.drawable.ph_map_pin_fill,
                tint = c.rose,
                size = 52.dp,
                modifier = Modifier.offset {
                    IntOffset(
                        (pinX - 26.dp.toPx()).roundToInt(),
                        (pinY - 52.dp.toPx() * 0.93f).roundToInt()
                    )
                }
            )
        }

        // ── Floating search bar ──────────────────────────────────────────────────────────────
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = ::runSearch,
            searching = searching,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ── My-location button, riding on top of the bottom card ─────────────────────────────
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp)
                .padding(bottom = navInset + 12.dp + 12.dp + with(density) { panelH.toDp() } + 12.dp)
        ) {
            MapFab(R.drawable.ph_crosshair, "Use my location", onClick = ::goToMyLocation)
        }

        // ── Floating bottom card ─────────────────────────────────────────────────────────────
        FloatingPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = navInset + 12.dp)
                .onSizeChanged { panelH = it.height },
            contentPadding = PaddingValues(16.dp)
        ) {
            // name
            Text("NAME", color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_NAMES.forEach { preset ->
                    ChoiceChip(
                        text = preset,
                        selected = !customChosen && presetName == preset,
                        onClick = {
                            customChosen = false
                            presetName = preset
                            if (preset == "Home") radius = GeofenceConstants.HOME_RADIUS_METERS
                        }
                    )
                }
                ChoiceChip(
                    text = if (customName.isBlank()) "Custom…" else customName,
                    selected = customChosen,
                    onClick = { showNameDialog = true }
                )
            }
            val homeExists = locations.any { it.name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true) }
            if (!customChosen && presetName == "Home" && homeExists) {
                Spacer(Modifier.height(6.dp))
                Text("Home already exists. Saving moves it here.", color = c.amber, fontSize = 11.5.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "RADIUS", color = c.textMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("${radius.roundToInt()} m", color = c.rose, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = radius,
                onValueChange = { radius = (it / 5f).roundToInt() * 5f },
                valueRange = GeofenceConstants.MIN_RADIUS_METERS..GeofenceConstants.MAX_RADIUS_METERS,
                colors = SliderDefaults.colors(
                    thumbColor = c.rose,
                    activeTrackColor = c.rose,
                    inactiveTrackColor = c.cardHigh
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_RADII.forEach { r ->
                    ChoiceChip(text = "${r.toInt()} m", selected = radius == r, onClick = { radius = r }, small = true)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (locating) "Locating…" else "My location",
                    onClick = ::goToMyLocation,
                    icon = R.drawable.ph_crosshair,
                    modifier = Modifier.weight(1f),
                    height = 54.dp
                )
                PrimaryButton(
                    text = "Save zone",
                    onClick = {
                        val center = pinLatLng()
                        if (center != null) {
                            viewModel.saveZone(name, center.latitude, center.longitude, radius) { onBack() }
                        }
                    },
                    icon = R.drawable.ph_check_bold,
                    enabled = boxH > 0 && map != null,
                    modifier = Modifier.weight(1f),
                    height = 54.dp
                )
            }
        }
    }

    if (showNameDialog) {
        var draft by remember { mutableStateOf(customName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = Color.White,
            title = { Text("Name this place", fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(30) },
                    singleLine = true,
                    placeholder = { Text("e.g. Grandma's house") },
                    shape = RoundedCornerShape(14.dp),
                    colors = rakshaFieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customName = draft.trim()
                    customChosen = true
                    showNameDialog = false
                }) { Text("Done", color = c.rose, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel", color = c.textMuted) }
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .floatingShadow(shape)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, c.border, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconChip(R.drawable.ph_arrow_left_bold, "Back", onBack, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(c.rose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search a place or address", color = c.textFaint, fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .bounceClick(onClick = onSearch)
                    .clip(CircleShape)
                    .background(c.rose),
                contentAlignment = Alignment.Center
            ) {
                if (searching) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp
                    )
                } else {
                    PhIcon(R.drawable.ph_magnifying_glass, tint = Color.White, size = 22.dp, contentDescription = "Search")
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, small: Boolean = false) {
    val c = Raksha.colors
    val shape = RoundedCornerShape(100)
    Box(
        Modifier
            .height(if (small) 32.dp else 38.dp)
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(if (selected) c.rose else c.card)
            .padding(horizontal = if (small) 14.dp else 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else c.text,
            fontSize = if (small) 12.5.sp else 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

/** Place search through OpenStreetMap Nominatim (needs a descriptive User-Agent). */
private fun geocode(query: String): LatLng? = try {
    val url = URL("https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=1")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("User-Agent", "RakshaGeoFencing/1.4 (safe-zone app)")
        setRequestProperty("Accept", "application/json")
    }
    try {
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(body)
        if (array.length() > 0) {
            val obj = array.getJSONObject(0)
            LatLng(obj.getString("lat").toDouble(), obj.getString("lon").toDouble())
        } else null
    } finally {
        conn.disconnect()
    }
} catch (e: Exception) {
    null
}
