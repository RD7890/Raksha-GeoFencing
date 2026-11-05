package com.rohan.raksha.geofence.data

import android.content.Context
import androidx.room.*

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE isEnabled = 1")
    fun getEnabled(): kotlinx.coroutines.flow.Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation): Long

    @Update
    suspend fun update(location: SavedLocation)

    @Delete
    suspend fun delete(location: SavedLocation)

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getById(id: Int): SavedLocation?
}

@Database(entities = [SavedLocation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "raksha_geofence.db"
                ).build().also { INSTANCE = it }
            }
    }
}
