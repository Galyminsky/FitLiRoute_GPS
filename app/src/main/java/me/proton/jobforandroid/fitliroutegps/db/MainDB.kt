package me.proton.jobforandroid.fitliroutegps.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackItem::class], version = 1, exportSchema = false)
abstract class MainDB : RoomDatabase() {

    companion object {
        @Volatile
        var INSTANCE: MainDB? = null
        fun getDatabase(context: Context): MainDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MainDB::class.java,
                    "GPSTracker.db"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}