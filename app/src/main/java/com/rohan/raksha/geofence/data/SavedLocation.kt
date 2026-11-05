package com.rohan.raksha.geofence.data

import androidx.room.*

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isEnabled: Boolean = true,
    val exitRadiusMeters: Float = 50f,
    val arrivalRadiusMeters: Float = 50f,
    val shieldEnabled: Boolean = false
)
