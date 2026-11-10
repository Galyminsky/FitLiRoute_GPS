package me.proton.jobforandroid.fitliroutegps.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackItem::class], version = 2, exportSchema = false)
abstract class MainDB : RoomDatabase() {
    abstract fun getDao(): Dao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        var INSTANCE: MainDB? = null
        fun getDatabase(context: Context): MainDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MainDB::class.java,
                    "GPSTracker.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
