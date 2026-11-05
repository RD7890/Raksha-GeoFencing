package com.rohan.raksha.geofence.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.ui.theme.*
import com.rohan.raksha.geofence.ui.viewmodel.MainViewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import com.rohan.raksha.geofence.ui.map.MapConfig
import com.rohan.raksha.geofence.ui.map.SafeZoneLayer
import com.rohan.raksha.geofence.ui.map.rememberMapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onNavigateAdd: () -> Unit, onNavigateSettings: () -> Unit) {
    val locations by viewModel.savedLocations.collectAsState()
    val locationsState = rememberUpdatedState(locations)
    val mapView = rememberMapView()
    var showBottomSheet by remember { mutableStateOf(true) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Full Screen Map
        AndroidView(
            factory = { ctx ->
                mapView.apply {
                    getMapAsync { map ->
                        mapRef = map
                        map.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { style ->
                            val locationComponent = map.locationComponent
                            val request = org.maplibre.android.location.engine.LocationEngineRequest.Builder(1000L)
                                .setPriority(org.maplibre.android.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                                .setMaxWaitTime(1000L)
                                .build()
                            val activationOptions = LocationComponentActivationOptions.builder(ctx, style)
                                .useDefaultLocationEngine(true)
                                .locationEngineRequest(request)
                                .build()
                            locationComponent.activateLocationComponent(activationOptions)
                            SafeZoneLayer.update(
                                style,
                                locationsState.value.map { LatLng(it.latitude, it.longitude) to it.exitRadiusMeters.toDouble() }
                            )
                            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                locationComponent.isLocationComponentEnabled = true
                                locationComponent.cameraMode = CameraMode.TRACKING
                                locationComponent.renderMode = RenderMode.GPS
                                locationComponent.zoomWhileTracking(15.0)
                            }
                        }
                    }
                }
            },
            update = { mapView ->
                mapView.getMapAsync { map ->
                    map.clear()
                    locations.forEach { loc ->
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(loc.latitude, loc.longitude))
                                .title(loc.name)
                        )
                    }
                    map.style?.let { style ->
                        SafeZoneLayer.update(
                            style,
                            locations.map { LatLng(it.latitude, it.longitude) to it.exitRadiusMeters.toDouble() }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top Bar Overlay
        TopAppBar(
            title = { 
                Text(
                    "Raksha GeoFencing", 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ) 
            },
            actions = {
                IconButton(onClick = onNavigateSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkSpaceNavy.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )
        
        // Bottom Sheet Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // Any touch on the map detaches the camera from the blue dot; this re-attaches it
                FloatingActionButton(
                    onClick = {
                        val component = mapRef?.locationComponent
                        if (component != null && component.isLocationComponentActivated) {
                            component.cameraMode = CameraMode.TRACKING
                            component.zoomWhileTracking(16.0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Follow my location")
                }

                FloatingActionButton(
                    onClick = { showBottomSheet = !showBottomSheet },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        if (showBottomSheet) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Locations"
                    )
                }
                
                FloatingActionButton(
                    onClick = onNavigateAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Location")
                }
            }

            AnimatedVisibility(
                visible = showBottomSheet,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                        
                        Text(
                            text = "Saved Locations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                        
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            items(locations) { loc ->
                                LocationCard(
                                    location = loc, 
                                    onToggle = { viewModel.toggleEnabled(loc) }, 
                                    onDelete = { viewModel.deleteLocation(loc) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationCard(location: SavedLocation, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = location.name, 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Safe zone: ${location.exitRadiusMeters.toInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = location.isEnabled, 
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
