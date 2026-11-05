package com.rohan.raksha.geofence.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.data.SavedLocation
import com.rohan.raksha.geofence.geofence.GeofenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val geofenceManager = GeofenceManager(application)

    val savedLocations: StateFlow<List<SavedLocation>> = db.savedLocationDao().getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
