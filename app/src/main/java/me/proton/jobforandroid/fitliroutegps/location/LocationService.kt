package me.proton.jobforandroid.fitliroutegps.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.os.Binder
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import me.proton.jobforandroid.fitliroutegps.MainActivity
import me.proton.jobforandroid.fitliroutegps.R
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ThreadLocalRandom

class LocationService : Service() {

    private var lastLocation: Location? = null
    private var distance = 0.0F
    private lateinit var locProvider: FusedLocationProviderClient
    private lateinit var locRequest: LocationRequest
    private lateinit var geoPointsList: ArrayList<GeoPoint>
    private var lastSpeed = 0.0f
    private val binder = LocalBinder()
    private val draftStore by lazy { TrackDraftStore(applicationContext) }
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Pair<TrackDraft, CompletableDeferred<Boolean>?>>(Channel.CONFLATED)
    private var draftId = 0
    private var sequence = 0L
    private var stopping = false
    private var starting = false

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    @Synchronized
    fun getTrackSnapshot(): LocationModel? {
        if (!isRunning) return null
        val points = ArrayList(geoPointsList.map { GeoPoint(it.latitude, it.longitude) })
        return LocationModel(lastSpeed, distance, points)
    }

    suspend fun checkpointForStop(): TrackDraft? {
        val draft = synchronized(this) {
            if (!isRunning || stopping) return null
            stopping = true
            currentDraft(false)
        }
        val saved = try {
            locProvider.removeLocationUpdates(locCallBack)
            val completion = CompletableDeferred<Boolean>()
            writes.send(draft to completion)
            completion.await()
        } catch (_: Exception) { false }
        if (!saved) {
            stopping = false
            startLocationUpdates()
            return null
        }
        return draft
    }

    private fun currentDraft(recording: Boolean) = TrackDraft(
        draftId, recording, startTime, distance, lastSpeed,
        lastLocation?.let { DraftPoint(it.latitude, it.longitude) },
        geoPointsList.map { DraftPoint(it.latitude, it.longitude) }, sequence, System.currentTimeMillis()
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (starting || isRunning) return START_NOT_STICKY
        if (intent?.action != ACTION_NEW && intent?.action != ACTION_RESUME) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        starting = true
        startNotification()
        persistenceScope.launch {
            try {
                val existing = draftStore.read()
                if (intent.action == ACTION_NEW && existing != null) throw IllegalStateException("Unresolved draft")
                val recovered = if (intent.action == ACTION_RESUME) existing else null
                if (intent.action == ACTION_RESUME && recovered == null) throw IllegalStateException("No draft")
                withContext(Dispatchers.Main) {
                    if (recovered != null) {
                        synchronized(this@LocationService) {
                            draftId = recovered.id
                            startTime = TrackDraftRecovery.resumedStartTime(recovered, System.currentTimeMillis())
                            distance = recovered.distance
                            lastSpeed = recovered.speed
                            lastLocation = recovered.lastPoint?.let { Location("draft").apply {
                                latitude = it.latitude; longitude = it.longitude
                            } }
                            geoPointsList = ArrayList(recovered.points.map { GeoPoint(it.latitude, it.longitude) })
                            sequence = recovered.sequence
                        }
                    } else {
                        draftId = ThreadLocalRandom.current().nextInt(1, Int.MAX_VALUE)
                    }
                }
                draftStore.write(currentDraft(true))
                withContext(Dispatchers.Main) {
                    if (!startLocationUpdates()) throw IllegalStateException("Location permission unavailable")
                    isRunning = true
                    starting = false
                    if (recovered != null && geoPointsList.isNotEmpty()) {
                        sendLocData(LocationModel(lastSpeed, distance, geoPointsList))
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    starting = false
                    LocalBroadcastManager.getInstance(applicationContext)
                        .sendBroadcast(Intent(ACTION_START_FAILED))
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        geoPointsList = ArrayList()
        initLocation()
        persistenceScope.launch {
            for ((draft, completion) in writes) {
                val success = try { draftStore.write(draft); true } catch (_: Exception) { false }
                completion?.complete(success)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        locProvider.removeLocationUpdates(locCallBack)
        persistenceScope.cancel()
    }

    private val locCallBack = object : LocationCallback() {
        override fun onLocationResult(lResult: LocationResult) {
            super.onLocationResult(lResult)
            synchronized(this@LocationService) {
                val currentLocation = lResult.lastLocation
                if (!stopping && lastLocation != null && currentLocation != null) {
                    lastSpeed = currentLocation.speed
                    distance += lastLocation?.distanceTo(currentLocation)!!
                    geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    sequence++
                    val locModel = LocationModel(
                        currentLocation.speed,
                        distance,
                        geoPointsList
                    )
                    sendLocData(locModel)
                }
                if (!stopping && currentLocation != null) {
                    lastLocation = currentLocation
                    writes.trySend(currentDraft(true) to null)
                }
            }
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
        locRequest = LocationRequest.create()
        locRequest.interval = 5000
        locRequest.fastestInterval = 2000
        locRequest.priority = PRIORITY_HIGH_ACCURACY
        locProvider = LocationServices.getFusedLocationProviderClient(baseContext)
    }

    private fun startLocationUpdates(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        locProvider.requestLocationUpdates(
            locRequest,
            locCallBack,
            Looper.myLooper()
        )
        return true
    }

    companion object {
        const val ACTION_NEW = "me.proton.jobforandroid.fitliroutegps.NEW_TRACK"
        const val ACTION_RESUME = "me.proton.jobforandroid.fitliroutegps.RESUME_TRACK"
        const val ACTION_START_FAILED = "me.proton.jobforandroid.fitliroutegps.START_FAILED"
        const val CHANNEL_ID = "channel_1"
        const val LOC_MODEL_INTENT = "loc_intent"
        var isRunning = false
        var startTime = 0L
    }
}
