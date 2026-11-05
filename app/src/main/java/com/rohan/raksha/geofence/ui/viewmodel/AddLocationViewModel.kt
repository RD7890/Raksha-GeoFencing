package com.rohan.raksha.geofence.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.raksha.geofence.data.AppDatabase
import com.rohan.raksha.geofence.data.SavedLocation
import kotlinx.coroutines.launch

class AddLocationViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    fun saveLocation(name: String, lat: Double, lng: Double, radiusMeters: Float, onDone: () -> Unit) {
        viewModelScope.launch {
            db.savedLocationDao().insert(
                SavedLocation(
                    name = name,
                    latitude = lat,
                    longitude = lng,
                    exitRadiusMeters = radiusMeters,
                    arrivalRadiusMeters = radiusMeters
                )
            )
            onDone()
        }
    }
}
