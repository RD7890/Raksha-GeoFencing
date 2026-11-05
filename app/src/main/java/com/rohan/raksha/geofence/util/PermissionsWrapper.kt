package com.rohan.raksha.geofence.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasBasicPermissions by remember { mutableStateOf(checkBasicPermissions(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(checkBackgroundLocation(context)) }
    var isGpsEnabled by remember { mutableStateOf(checkGpsEnabled(context)) }
    var fullScreenOk by remember { mutableStateOf(FullScreenIntentUtil.canUse(context)) }
    var skipFullScreen by remember { mutableStateOf(false) }

    val basicPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBasicPermissions = checkBasicPermissions(context)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasBackgroundLocation = checkBackgroundLocation(context)
    }
    
    val gpsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isGpsEnabled = checkGpsEnabled(context)
    }

    val fullScreenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        fullScreenOk = FullScreenIntentUtil.canUse(context)
    }

    LaunchedEffect(Unit) {
        if (!hasBasicPermissions) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            basicPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    if (!hasBasicPermissions) {
        PermissionScreen(
            title = "Permissions Required",
            description = "Raksha GeoFencing requires location and notification permissions to function properly. Please grant them to continue.",
            buttonText = "Grant Permissions",
            onClick = {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                basicPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            }
        )
    } else if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PermissionScreen(
            title = "Background Location",
            description = "To ensure geofencing works even when the app is closed, please select 'Allow all the time' in the next prompt.",
            buttonText = "Allow Background Location",
            onClick = {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        )
    } else if (!isGpsEnabled) {
        PermissionScreen(
            title = "Enable GPS",
            description = "Location services are disabled. Please enable GPS to use geofencing.",
            buttonText = "Open Settings",
            onClick = {
                gpsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        )
    } else if (!fullScreenOk && !skipFullScreen) {
        PermissionScreen(
            title = "Full-screen Alerts",
            description = "To open the red alert screen over your lock screen when you leave the safe zone, allow full-screen notifications for Raksha.",
            buttonText = "Allow Full-screen Alerts",
            onClick = {
                try {
                    fullScreenLauncher.launch(FullScreenIntentUtil.settingsIntent(context))
                } catch (e: Exception) {
                    skipFullScreen = true
                }
            },
            secondaryText = "Skip for now",
            onSecondary = { skipFullScreen = true }
        )
    } else {
        content()
    }
}

private fun checkBasicPermissions(context: Context): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    return fineLocation && coarseLocation && notifications
}

private fun checkBackgroundLocation(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else true
}

private fun checkGpsEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
           locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@Composable
fun PermissionScreen(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = buttonText, color = MaterialTheme.colorScheme.onPrimary)
                }
                if (secondaryText != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) {
                        Text(text = secondaryText)
                    }
                }
            }
        }
    }
}
