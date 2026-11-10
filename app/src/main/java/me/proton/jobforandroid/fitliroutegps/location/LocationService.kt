package me.proton.jobforandroid.fitliroutegps.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Binder
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.resume

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
    private val draftWriter = ConflatedDraftWriter { draftStore.write(it) }
    private var draftId = 0
    private var sequence = 0L
    private var stopping = false
    private var starting = false
    private var registered = false
    private var registrationPending = false
    private var removalPending = false
    private val gpsState = GpsRecordingState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFixAt = 0L
    private val availabilityCheck: Runnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                if (!canUseGps()) {
                    markUnavailable()
                    dropLocationUpdates()
                } else if (!registered && !registrationPending && !removalPending) {
                    retryLocationUpdates()
                } else if (registered && System.currentTimeMillis() - lastFixAt >= NO_FIX_TIMEOUT_MS) {
                    markUnavailable()
                    dropLocationUpdates()
                }
                mainHandler.postDelayed(this, AVAILABILITY_CHECK_MS)
            }
        }
    }
    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION && isRunning) {
                mainHandler.removeCallbacks(availabilityCheck)
                mainHandler.post(availabilityCheck)
            }
        }
    }

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
            draftWriter.writeAndAwait(draft)
        } catch (_: Exception) { false }
        if (!saved) {
            stopping = false
            if (canUseGps()) retryLocationUpdates() else markUnavailable()
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
                            val restoredPoints = TrackFixPolicy.restoredPoints(recovered)
                            geoPointsList = ArrayList(restoredPoints.map { GeoPoint(it.latitude, it.longitude) })
                            sequence = recovered.sequence + (restoredPoints.size - recovered.points.size)
                        }
                    } else {
                        draftId = ThreadLocalRandom.current().nextInt(1, Int.MAX_VALUE)
                    }
                }
                if (!draftWriter.writeAndAwait(currentDraft(true)))
                    throw IllegalStateException("Draft checkpoint failed")
                withContext(Dispatchers.Main) {
                    if (!startLocationUpdates()) throw IllegalStateException("Location updates unavailable")
                    isRunning = true
                    starting = false
                    gpsState.registrationSucceeded()
                    gpsAvailable = true
                    lastFixAt = System.currentTimeMillis()
                    mainHandler.postDelayed(availabilityCheck, AVAILABILITY_CHECK_MS)
                    LocalBroadcastManager.getInstance(applicationContext)
                        .sendBroadcast(Intent(ACTION_START_CONFIRMED))
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
        registerReceiver(providerReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    override fun onDestroy() {
        super.onDestroy()
        val finalSnapshot = synchronized(this) {
            if (isRunning && !stopping) currentDraft(true) else null
        }
        isRunning = false
        gpsAvailable = false
        gpsState.stopped()
        registered = false
        mainHandler.removeCallbacks(availabilityCheck)
        runCatching { unregisterReceiver(providerReceiver) }
        runCatching { locProvider.removeLocationUpdates(locCallBack) }
        draftWriter.finish(finalSnapshot)
        persistenceScope.cancel()
    }

    private val locCallBack = object : LocationCallback() {
        override fun onLocationResult(lResult: LocationResult) {
            super.onLocationResult(lResult)
            synchronized(this@LocationService) {
                if (stopping || !registered || !canUseGps()) return
                val currentLocation = lResult.lastLocation ?: return
                val currentPoint = DraftPoint(currentLocation.latitude, currentLocation.longitude)
                val previousPoint = lastLocation?.let { DraftPoint(it.latitude, it.longitude) }
                val lastRoutePoint = geoPointsList.lastOrNull()?.let { DraftPoint(it.latitude, it.longitude) }
                val decision = TrackFixPolicy.decide(previousPoint, lastRoutePoint,
                    geoPointsList.size, currentPoint) ?: return
                lastFixAt = System.currentTimeMillis()
                markRestored()
                if (decision.addDistance) {
                    lastSpeed = currentLocation.speed
                    distance += lastLocation?.distanceTo(currentLocation)!!
                }
                if (decision.appendPoint) {
                    geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    sequence++
                }
                sendLocData(LocationModel(currentLocation.speed, distance, geoPointsList))
                lastLocation = currentLocation
                draftWriter.offer(currentDraft(true))
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

    private fun canUseGps(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                .isProviderEnabled(LocationManager.GPS_PROVIDER)

    private suspend fun startLocationUpdates(): Boolean {
        if (!canUseGps()) {
            registrationPending = false
            return false
        }
        registrationPending = true
        return try {
            val success = suspendCancellableCoroutine<Boolean> { continuation ->
                try {
                    locProvider.requestLocationUpdates(locRequest, locCallBack, Looper.getMainLooper())
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            registered = success && canUseGps()
            if (success && !registered) locProvider.removeLocationUpdates(locCallBack)
            registered
        } finally {
            registrationPending = false
        }
    }

    private fun retryLocationUpdates() {
        registrationPending = true
        persistenceScope.launch {
            val success = try { withContext(Dispatchers.Main) { startLocationUpdates() } }
                catch (_: Exception) { false }
            withContext(Dispatchers.Main) {
                if (isRunning && !stopping && success) {
                    lastFixAt = System.currentTimeMillis()
                } else if (isRunning && !stopping) markUnavailable()
            }
        }
    }

    private fun dropLocationUpdates() {
        if (!registered) return
        registered = false
        removalPending = true
        try {
            locProvider.removeLocationUpdates(locCallBack).addOnCompleteListener {
                removalPending = false
                if (isRunning) mainHandler.post(availabilityCheck)
            }
        } catch (_: Exception) {
            removalPending = false
        }
    }

    private fun markUnavailable() {
        if (gpsState.value == GpsRecordingState.Value.UNAVAILABLE) return
        gpsState.unavailable()
        gpsAvailable = false
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(ACTION_GPS_UNAVAILABLE))
    }

    private fun markRestored() {
        if (gpsState.value == GpsRecordingState.Value.ACTIVE) return
        gpsState.registrationSucceeded()
        gpsAvailable = true
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(ACTION_GPS_RESTORED))
    }

    companion object {
        const val ACTION_NEW = "me.proton.jobforandroid.fitliroutegps.NEW_TRACK"
        const val ACTION_RESUME = "me.proton.jobforandroid.fitliroutegps.RESUME_TRACK"
        const val ACTION_START_FAILED = "me.proton.jobforandroid.fitliroutegps.START_FAILED"
        const val ACTION_START_CONFIRMED = "me.proton.jobforandroid.fitliroutegps.START_CONFIRMED"
        const val ACTION_GPS_UNAVAILABLE = "me.proton.jobforandroid.fitliroutegps.GPS_UNAVAILABLE"
        const val ACTION_GPS_RESTORED = "me.proton.jobforandroid.fitliroutegps.GPS_RESTORED"
        private const val AVAILABILITY_CHECK_MS = 5_000L
        private const val NO_FIX_TIMEOUT_MS = 30_000L
        const val CHANNEL_ID = "channel_1"
        const val LOC_MODEL_INTENT = "loc_intent"
        var isRunning = false
        @Volatile var gpsAvailable = false
        var startTime = 0L
    }
}
