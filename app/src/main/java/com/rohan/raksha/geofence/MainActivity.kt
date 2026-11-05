package com.rohan.raksha.geofence

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rohan.raksha.geofence.ui.components.FloatingBottomBar
import com.rohan.raksha.geofence.ui.screen.AddLocationScreen
import com.rohan.raksha.geofence.ui.screen.MapHomeScreen
import com.rohan.raksha.geofence.ui.screen.SettingsScreen
import com.rohan.raksha.geofence.ui.screen.ZonesScreen
import com.rohan.raksha.geofence.ui.theme.Raksha
import com.rohan.raksha.geofence.ui.theme.RakshaGeoFencingTheme
import com.rohan.raksha.geofence.ui.viewmodel.MainViewModel
import org.maplibre.android.MapLibre

private val TAB_ROUTES = listOf("map", "zones", "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        setContent {
            RakshaGeoFencingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Raksha.colors.bg) {
                    com.rohan.raksha.geofence.util.PermissionsWrapper {
                        val navController = rememberNavController()
                        // One view-model for the whole app: keeps the geofences in sync and shares zones between tabs
                        val vm: MainViewModel = viewModel()
                        val backStack by navController.currentBackStackEntryAsState()
                        val route = backStack?.destination?.route
                        val tabIndex = TAB_ROUTES.indexOf(route)

                        Box(Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = "map",
                                enterTransition = { fadeIn(tween(220)) },
                                exitTransition = { fadeOut(tween(160)) },
                                popEnterTransition = { fadeIn(tween(220)) },
                                popExitTransition = { fadeOut(tween(160)) }
                            ) {
                                composable("map") {
                                    MapHomeScreen(viewModel = vm, onAddZone = { navController.navigate("add") })
                                }
                                composable("zones") {
                                    ZonesScreen(
                                        viewModel = vm,
                                        onAddZone = { navController.navigate("add") },
                                        onOpenOnMap = { navController.switchTab("map") }
                                    )
                                }
                                composable("settings") { SettingsScreen() }
                                composable(
                                    "add",
                                    enterTransition = { slideInVertically(tween(320)) { it / 8 } + fadeIn(tween(260)) },
                                    popExitTransition = { slideOutVertically(tween(260)) { it / 8 } + fadeOut(tween(200)) }
                                ) {
                                    AddLocationScreen(viewModel = vm, onBack = { navController.popBackStack() })
                                }
                            }

                            if (tabIndex >= 0) {
                                FloatingBottomBar(
                                    selected = tabIndex,
                                    onSelect = { navController.switchTab(TAB_ROUTES[it]) },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tabs replace each other instead of piling up; back from any tab goes to the map. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo("map") { inclusive = route == "map" }
        launchSingleTop = true
    }
}
