package me.proton.jobforandroid.fitliroutegps

import android.app.Application
import me.proton.jobforandroid.fitliroutegps.db.MainDB

class MainApp : Application() {

    val database by lazy { MainDB.getDatabase(this) }

}