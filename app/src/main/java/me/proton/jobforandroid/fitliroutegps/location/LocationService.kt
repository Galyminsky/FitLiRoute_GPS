package me.proton.jobforandroid.fitliroutegps.location

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LocationService : Service(){
    override fun onBind(intent: Intent?): IBinder? {
       return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}