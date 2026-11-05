package com.rohan.raksha.geofence.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceConstants
import com.rohan.raksha.geofence.geofence.GeofenceManager
import com.rohan.raksha.geofence.util.CurrentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of the one-tap "Set Home to my location" action. */
sealed interface HomeResult {
    data class Saved(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val movedExisting: Boolean
    ) : HomeResult

    /** No GPS fix could be obtained (permission, GPS off, indoors...). */
    data object NoFix : HomeResult
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val geofenceManager = GeofenceManager(application)

    val savedLocations: StateFlow<List<SavedLocation>> = db.savedLocationDao().getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Zone the Zones tab asked the map to fly to. */
    private val _focusZoneId = MutableStateFlow<Int?>(null)
    val focusZoneId: StateFlow<Int?> = _focusZoneId

    fun focusZone(id: Int) { _focusZoneId.value = id }
    fun consumeFocus() { _focusZoneId.value = null }

    init {
        // Re-register the outer-ring geofences whenever the saved zones change
        // (add / delete / enable / radius edit). Collecting the DB flow directly avoids
        // reacting to the empty initial value of the StateFlow above.
        viewModelScope.launch {
            db.savedLocationDao().getAll().collect { locations ->
                geofenceManager.sync(locations)
            }
        }
    }

    /**
     * Puts the "Home" safe zone (30 m) exactly where the phone is right now. Creates it, or moves
     * the existing Home there and re-enables it.
     */
    fun setHomeHere(onResult: (HomeResult) -> Unit) {
        viewModelScope.launch {
            val fix = CurrentLocation.get(getApplication())
            if (fix == null) {
                onResult(HomeResult.NoFix)
                return@launch
            }
            val dao = db.savedLocationDao()
            val existing = dao.getAll().first()
                .firstOrNull { it.name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true) }
            if (existing != null) {
                dao.update(
                    existing.copy(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        exitRadiusMeters = GeofenceConstants.HOME_RADIUS_METERS,
                        arrivalRadiusMeters = GeofenceConstants.HOME_RADIUS_METERS,
                        isEnabled = true
                    )
                )
            } else {
                dao.insert(
                    SavedLocation(
                        name = GeofenceConstants.HOME_NAME,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        exitRadiusMeters = GeofenceConstants.HOME_RADIUS_METERS,
                        arrivalRadiusMeters = GeofenceConstants.HOME_RADIUS_METERS
                    )
                )
            }
            onResult(
                HomeResult.Saved(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else -1f,
                    movedExisting = existing != null
                )
            )
        }
    }

    /** Saves a zone picked on the map. Saving a zone called "Home" moves the existing Home instead of duplicating it. */
    fun saveZone(name: String, latitude: Double, longitude: Double, radiusMeters: Float, onDone: () -> Unit) {
        viewModelScope.launch {
            val dao = db.savedLocationDao()
            val existing = if (name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true)) {
                dao.getAll().first().firstOrNull { it.name.equals(GeofenceConstants.HOME_NAME, ignoreCase = true) }
            } else null
            if (existing != null) {
                dao.update(
                    existing.copy(
                        latitude = latitude, longitude = longitude,
                        exitRadiusMeters = radiusMeters, arrivalRadiusMeters = radiusMeters,
                        isEnabled = true
                    )
                )
            } else {
                dao.insert(
                    SavedLocation(
                        name = name, latitude = latitude, longitude = longitude,
                        exitRadiusMeters = radiusMeters, arrivalRadiusMeters = radiusMeters
                    )
                )
            }
            onDone()
        }
    }

    fun toggleEnabled(location: SavedLocation) {
        viewModelScope.launch {
            db.savedLocationDao().update(location.copy(isEnabled = !location.isEnabled))
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            db.savedLocationDao().delete(location)
        }
    }
}
