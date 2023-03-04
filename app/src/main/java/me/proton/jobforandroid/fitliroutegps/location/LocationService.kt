package me.proton.jobforandroid.fitliroutegps.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import me.proton.jobforandroid.fitliroutegps.MainActivity
import me.proton.jobforandroid.fitliroutegps.R
import org.osmdroid.util.GeoPoint

class LocationService : Service() {

    private var lastLocation: Location? = null
    private var distance = 0.0F
    private var isDebug = false
    private lateinit var locProvider: FusedLocationProviderClient
    private lateinit var locRequest: LocationRequest
    private lateinit var geoPointsList: ArrayList<GeoPoint>


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startNotification()
        startLocationUpdates()
        isRunning = true
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        geoPointsList = ArrayList()
        initLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        locProvider.removeLocationUpdates(locCallBack)
    }

    private val locCallBack = object : LocationCallback() {
        override fun onLocationResult(lResult: LocationResult) {
            super.onLocationResult(lResult)
            val currentLocation = lResult.lastLocation
            if (lastLocation != null && currentLocation != null) {
                if (currentLocation.speed > 0.4 || isDebug) {
                    distance += lastLocation?.distanceTo(currentLocation)!!
                    geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                }
                val locModel = LocationModel(
                    currentLocation.speed,
                    distance,
                    geoPointsList
                )
                sendLocData(locModel)
            }
            lastLocation = currentLocation
        }
    }

    private fun sendLocData(locModel: LocationModel) {
        val i = Intent(LOC_MODEL_INTENT)
        i.putExtra(LOC_MODEL_INTENT, locModel)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(i)
    }

    private fun startNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nManager = getSystemService(NotificationManager::class.java) as NotificationManager
            nManager.createNotificationChannel(nChannel)
        }

        val nIntent = Intent(this, MainActivity::class.java)
        val pIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                this,
                10,
                nIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                10,
                nIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        ).setSmallIcon(R.drawable.ic_ads_remove)
            .setContentTitle("Tracker running")
            .setContentIntent(pIntent).build()
        startForeground(99, notification)
    }

    private fun initLocation() {
        val updateInterval = PreferenceManager.getDefaultSharedPreferences(
            this,
        ).getString("update_time_key", "3000")?.toLong() ?: 3000
        locRequest = LocationRequest.create()
        locRequest.interval = updateInterval
        locRequest.fastestInterval = updateInterval
        locRequest.priority = PRIORITY_HIGH_ACCURACY
        locProvider = LocationServices.getFusedLocationProviderClient(baseContext)
        Log.d("MyLog", "Interval: $updateInterval")
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locProvider.requestLocationUpdates(
            locRequest,
            locCallBack,
            Looper.myLooper()
        )
    }
    companion object {
        const val CHANNEL_ID = "channel_1"
        const val LOC_MODEL_INTENT = "loc_intent"
        var isRunning = false
        var startTime = 0L
    }
}
