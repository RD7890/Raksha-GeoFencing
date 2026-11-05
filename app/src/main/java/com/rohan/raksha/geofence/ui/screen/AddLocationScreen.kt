package com.rohan.raksha.geofence.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.ui.map.MapConfig
import com.rohan.raksha.geofence.ui.map.SafeZoneLayer
import com.rohan.raksha.geofence.ui.map.rememberMapView
import com.rohan.raksha.geofence.ui.viewmodel.AddLocationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(viewModel: AddLocationViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(GeofenceConstants.DEFAULT_RADIUS_METERS) }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val scope = rememberCoroutineScope()
    val mapView = rememberMapView()
    val radiusState = rememberUpdatedState(radius)
    val animatedRadius by animateFloatAsState(targetValue = radius, animationSpec = tween(200), label = "radius")

    // Redraw the safe-zone circle whenever the radius slider moves
    LaunchedEffect(radius, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val center = map.cameraPosition.target ?: return@LaunchedEffect
        SafeZoneLayer.update(style, listOf(center to radius.toDouble()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Safe Zone", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search a place or address") },
                trailingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotEmpty()) {
                            scope.launch {
                                val url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}&format=json"
                                val result = withContext(Dispatchers.IO) {
                                    try { URL(url).readText() } catch (e: Exception) { null }
                                }
                                if (result != null) {
                                    try {
                                        val array = JSONArray(result)
                                        if (array.length() > 0) {
                                            val obj = array.getJSONObject(0)
                                            val lat = obj.getString("lat").toDouble()
                                            val lon = obj.getString("lon").toDouble()
                                            mapLibreMap?.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 17.0), 1000
                                            )
                                        }
                                    } catch (e: Exception) { }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            // Map with live safe-zone circle (centre = the pin)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        mapView.apply {
                            getMapAsync { map ->
                                mapLibreMap = map
                                map.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { style ->
                                    val locationComponent = map.locationComponent
                                    locationComponent.activateLocationComponent(
                                        LocationComponentActivationOptions.builder(ctx, style).build()
                                    )
                                    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        locationComponent.isLocationComponentEnabled = true
                                        locationComponent.cameraMode = CameraMode.TRACKING
                                        locationComponent.renderMode = RenderMode.COMPASS
                                        locationComponent.zoomWhileTracking(17.0)
                                    }
                                    map.cameraPosition.target?.let {
                                        SafeZoneLayer.update(style, listOf(it to radiusState.value.toDouble()))
                                    }
                                }
                                map.addOnCameraMoveListener {
                                    val target = map.cameraPosition.target
                                    val style = map.style
                                    if (target != null && style != null) {
                                        SafeZoneLayer.update(style, listOf(target to radiusState.value.toDouble()))
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Static centre pin: the safe zone is created where the pin points
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Center Pin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .offset(y = (-24).dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name (e.g. Home)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "Safe zone radius: ${animatedRadius.roundToInt()} m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = radius,
                            onValueChange = { radius = (it / 5f).roundToInt() * 5f },
                            valueRange = GeofenceConstants.MIN_RADIUS_METERS..GeofenceConstants.MAX_RADIUS_METERS,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            "Minimum ${GeofenceConstants.MIN_RADIUS_METERS.toInt()} m. Smaller zones are not reliable with GPS. You get a full-screen alert when you leave this circle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val target = mapLibreMap?.cameraPosition?.target
                        if (target != null) {
                            viewModel.saveLocation(
                                name.ifEmpty { "Home" },
                                target.latitude, target.longitude,
                                radius
                            ) { onBack() }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Save Safe Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
