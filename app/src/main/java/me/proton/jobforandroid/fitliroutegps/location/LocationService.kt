package me.proton.jobforandroid.fitliroutegps.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
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
import kotlin.math.hypot

private const val MAX_LOCATION_ACCURACY_METERS = 25f
private const val MIN_MOVEMENT_THRESHOLD_METERS = 2f
private const val MOVEMENT_UNCERTAINTY_MULTIPLIER = 1.5f
private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 12.0
private const val MAX_CONTIGUOUS_GAP_SECONDS = 30.0

private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
    return latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}

class LocationService : Service() {

    private var distanceAnchor: Location? = null
    private var pendingLocation: Location? = null
    private var lastObservedLocation: Location? = null
    private var distance = 0.0F
    private var recordingStartTime = 0L
    private var startRequestPending = false
    private var isStopping = false
    private var restoredTrack = false
    private var needsFreshBaseline = false
    private var preserveRecoveryStateOnDestroy = false
    private var isForeground = false
    private lateinit var locProvider: FusedLocationProviderClient
    private lateinit var locRequest: LocationRequest
    private lateinit var geoPointsList: ArrayList<GeoPoint>

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            sendServiceEvent(ACTION_RECORDING_STARTED)
            return START_STICKY
        }
        if (startRequestPending) return START_STICKY

        val shouldRestore = intent == null || intent.action == ACTION_RESUME
        if (shouldRestore) {
            val savedTrack = readSavedTrack(this)
            if (savedTrack == null) {
                clearSavedTrack(this)
                if (intent != null) sendServiceEvent(ACTION_RECORDING_FAILED)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            restoreTrack(savedTrack)
            restoredTrack = true
        } else if (intent?.action == ACTION_START) {
            clearSavedTrack(this)
            beginNewTrack()
        } else {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!hasRequiredLocationPermission() || !isLocationEnabled()) {
            failToStart(startId, null)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification("Starting tracker"))
            isForeground = true
        } catch (error: RuntimeException) {
            failToStart(startId, error)
            return START_NOT_STICKY
        }

        startRequestPending = true
        try {
            locProvider.requestLocationUpdates(locRequest, locCallBack, Looper.getMainLooper())
                .addOnSuccessListener {
                    startRequestPending = false
                    isRunning = true
                    startTime = recordingStartTime
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("Tracker running"))
                    sendServiceEvent(ACTION_RECORDING_STARTED)
                }
                .addOnFailureListener { error ->
                    failToStart(startId, error)
                }
        } catch (error: RuntimeException) {
            failToStart(startId, error)
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        geoPointsList = ArrayList()
        initLocation()
    }

    override fun onDestroy() {
        isStopping = true
        isRunning = false
        startRequestPending = false
        locProvider.removeLocationUpdates(locCallBack)
        if (!preserveRecoveryStateOnDestroy) Companion.clearSavedTrack(this)
        if (isForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private val locCallBack = object : LocationCallback() {
        override fun onLocationResult(lResult: LocationResult) {
            super.onLocationResult(lResult)
            Log.d(DIAGNOSTIC_TAG, "callback locations=${lResult.locations.size}")
            lResult.locations.forEach { processLocation(it) }
        }
    }

    private fun processLocation(currentLocation: Location) {
        val previousObserved = lastObservedLocation
        val distanceToPreviousFix = previousObserved
            ?.takeIf {
                isValidCoordinate(it.latitude, it.longitude) &&
                    isValidCoordinate(currentLocation.latitude, currentLocation.longitude)
            }
            ?.distanceTo(currentLocation)
        lastObservedLocation = Location(currentLocation)

        var decision = "UNCLASSIFIED"
        var distanceFromAnchorForLog: Float? = null
        try {
            if (isStopping) {
                decision = "IGNORED_STOPPING"
                return
            }
            sendCurrentSpeed(currentLocation.speed)
            if (!hasUsableAccuracy(currentLocation)) {
                decision = "REJECTED_INVALID_OR_INACCURATE"
                return
            }

            if (needsFreshBaseline || distanceAnchor == null) {
                distanceAnchor = Location(currentLocation)
                pendingLocation = null
                needsFreshBaseline = false
                if (geoPointsList.isEmpty()) {
                    geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                }
                persistTrack()
                sendLocData(LocationModel(currentLocation.speed, distance, ArrayList(geoPointsList)))
                decision = "BASELINE"
                return
            }

            val anchor = distanceAnchor ?: run {
                decision = "IGNORED_NO_ANCHOR"
                return
            }
            val elapsedSeconds =
                (currentLocation.elapsedRealtimeNanos - anchor.elapsedRealtimeNanos) / 1_000_000_000.0
            if (elapsedSeconds <= 0.0 || elapsedSeconds > MAX_CONTIGUOUS_GAP_SECONDS) {
                rebaseDistanceAnchor(currentLocation)
                decision = "REBASE_TIME_GAP"
                return
            }

            val distanceFromAnchor = anchor.distanceTo(currentLocation)
            distanceFromAnchorForLog = distanceFromAnchor
            val uncertainty = combinedAccuracyMeters(anchor, currentLocation)
            val maximumPlausibleDistance =
                MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND * elapsedSeconds
            if (!distanceFromAnchor.isFinite() || distanceFromAnchor > maximumPlausibleDistance) {
                rebaseDistanceAnchor(currentLocation)
                decision = "REBASE_IMPLAUSIBLE_JUMP"
                return
            }

            val movementThreshold = maxOf(
                MIN_MOVEMENT_THRESHOLD_METERS,
                uncertainty * MOVEMENT_UNCERTAINTY_MULTIPLIER
            )
            val candidate = pendingLocation
            if (candidate == null) {
                if (distanceFromAnchor > movementThreshold) {
                    pendingLocation = Location(currentLocation)
                    decision = "PENDING_OVER_MOVEMENT_THRESHOLD"
                } else {
                    decision = "REJECTED_BELOW_MOVEMENT_THRESHOLD"
                }
                return
            }

            val candidateToCurrent = candidate.distanceTo(currentLocation)
            val candidateUncertainty = combinedAccuracyMeters(candidate, currentLocation)
            val candidateElapsedSeconds =
                (currentLocation.elapsedRealtimeNanos - candidate.elapsedRealtimeNanos) /
                    1_000_000_000.0
            val movementContinuesAwayFromAnchor =
                distanceFromAnchor >= anchor.distanceTo(candidate) - candidateUncertainty
            val confirmationMovementThreshold = maxOf(1f, candidateUncertainty * 0.5f)
            val candidateConfirmed = candidateToCurrent >= confirmationMovementThreshold &&
                movementContinuesAwayFromAnchor && candidateElapsedSeconds > 0.0 &&
                candidateToCurrent <= MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND *
                candidateElapsedSeconds

            if (candidateConfirmed) {
                distance += anchor.distanceTo(candidate)
                distanceAnchor = Location(candidate)
                geoPointsList.add(GeoPoint(candidate.latitude, candidate.longitude))
                pendingLocation = null
                persistTrack()
                sendLocData(LocationModel(currentLocation.speed, distance, ArrayList(geoPointsList)))

                val distanceAfterCandidate = distanceAnchor!!.distanceTo(currentLocation)
                val nextThreshold = maxOf(
                    MIN_MOVEMENT_THRESHOLD_METERS,
                    combinedAccuracyMeters(distanceAnchor!!, currentLocation) *
                        MOVEMENT_UNCERTAINTY_MULTIPLIER
                )
                if (distanceAfterCandidate > nextThreshold) {
                    pendingLocation = Location(currentLocation)
                }
                decision = "ACCEPTED_CONFIRMED_MOVEMENT"
            } else if (distanceFromAnchor <= movementThreshold) {
                pendingLocation = null
                decision = "DROPPED_PENDING_AS_STATIONARY"
            } else {
                pendingLocation = Location(currentLocation)
                decision = "REPLACED_PENDING_CANDIDATE"
            }
        } finally {
            Log.d(
                DIAGNOSTIC_TAG,
                "fixTimeMs=${currentLocation.time} elapsedNs=${currentLocation.elapsedRealtimeNanos} " +
                    "lat=${currentLocation.latitude} lon=${currentLocation.longitude} " +
                    "accuracyM=${if (currentLocation.hasAccuracy()) currentLocation.accuracy else "missing"} " +
                    "speedMps=${if (currentLocation.hasSpeed()) currentLocation.speed else "missing"} " +
                    "rawDeltaM=${distanceToPreviousFix ?: "n/a"} " +
                    "anchorDeltaM=${distanceFromAnchorForLog ?: "n/a"} " +
                    "decision=$decision totalDistanceM=$distance"
            )
        }
    }

    private fun hasUsableAccuracy(location: Location): Boolean {
        return isValidCoordinate(location.latitude, location.longitude) &&
            location.hasAccuracy() && location.accuracy.isFinite() &&
            location.accuracy in 0f..MAX_LOCATION_ACCURACY_METERS &&
            location.elapsedRealtimeNanos > 0L
    }

    private fun rebaseDistanceAnchor(location: Location) {
        distanceAnchor = Location(location)
        pendingLocation = null
        persistTrack()
    }

    private fun combinedAccuracyMeters(first: Location, second: Location): Float {
        return hypot(first.accuracy.toDouble(), second.accuracy.toDouble()).toFloat()
    }

    private fun beginNewTrack() {
        distanceAnchor = null
        pendingLocation = null
        distance = 0.0F
        geoPointsList.clear()
        recordingStartTime = System.currentTimeMillis()
        startTime = recordingStartTime
        restoredTrack = false
        needsFreshBaseline = false
        preserveRecoveryStateOnDestroy = false
        isStopping = false
    }

    private fun restoreTrack(savedTrack: SavedTrack) {
        geoPointsList.clear()
        geoPointsList.addAll(savedTrack.geoPoints)
        distance = savedTrack.distance
        recordingStartTime = savedTrack.startTime
        distanceAnchor = Location(savedTrack.distanceAnchor)
        pendingLocation = null
        startTime = recordingStartTime
        restoredTrack = true
        needsFreshBaseline = savedTrack.needsFreshBaseline
        preserveRecoveryStateOnDestroy = false
        isStopping = false
    }

    private fun persistTrack() {
        val anchor = distanceAnchor ?: return
        if (recordingStartTime <= 0L || geoPointsList.isEmpty()) return

        val points = buildString {
            geoPointsList.forEach { point ->
                append(point.latitude)
                append(',')
                append(point.longitude)
                append('/')
            }
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAS_ACTIVE_TRACK, true)
            .putLong(KEY_START_TIME, recordingStartTime)
            .putFloat(KEY_DISTANCE, distance)
            .putString(KEY_GEO_POINTS, points)
            .putString(KEY_ANCHOR_LATITUDE, anchor.latitude.toString())
            .putString(KEY_ANCHOR_LONGITUDE, anchor.longitude.toString())
            .putFloat(KEY_ANCHOR_ACCURACY, anchor.accuracy)
            .putLong(KEY_ANCHOR_ELAPSED_REALTIME, anchor.elapsedRealtimeNanos)
            .apply()
    }

    private fun failToStart(startId: Int, error: Exception?) {
        startRequestPending = false
        isStopping = true
        isRunning = false
        if (error != null) Log.e(TAG, "Unable to start location updates", error)

        if (restoredTrack) {
            preserveRecoveryStateOnDestroy = true
        } else {
            Companion.clearSavedTrack(this)
        }

        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        sendServiceEvent(ACTION_RECORDING_FAILED)
        stopSelf(startId)
    }

    private fun hasRequiredLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun sendLocData(locModel: LocationModel) {
        Log.d(
            DIAGNOSTIC_TAG,
            "broadcast distanceM=${locModel.distance} speedMps=${locModel.velocity} points=${locModel.geoPointsList.size}"
        )
        val intent = Intent(LOC_MODEL_INTENT)
        intent.putExtra(LOC_MODEL_INTENT, locModel)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun sendCurrentSpeed(speedMetersPerSecond: Float) {
        val intent = Intent(CURRENT_SPEED_INTENT)
        intent.putExtra(CURRENT_SPEED_INTENT, speedMetersPerSecond)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun sendServiceEvent(action: String) {
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(Intent(action))
    }

    private fun buildNotification(title: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_ads_remove)
        .setContentTitle(title)
        .setContentIntent(createContentIntent())
        .build()

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun initLocation() {
        locRequest = LocationRequest.create()
        locRequest.interval = 5000
        locRequest.fastestInterval = 2000
        locRequest.priority = PRIORITY_HIGH_ACCURACY
        locProvider = LocationServices.getFusedLocationProviderClient(baseContext)
        createNotificationChannel()
    }

    private data class SavedTrack(
        val startTime: Long,
        val distance: Float,
        val geoPoints: ArrayList<GeoPoint>,
        val distanceAnchor: Location,
        val needsFreshBaseline: Boolean
    )

    companion object {
        const val CHANNEL_ID = "channel_1"
        const val LOC_MODEL_INTENT = "loc_intent"
        const val CURRENT_SPEED_INTENT = "current_speed_intent"
        const val ACTION_START = "me.proton.jobforandroid.fitliroutegps.action.START_TRACK"
        const val ACTION_RESUME = "me.proton.jobforandroid.fitliroutegps.action.RESUME_TRACK"
        const val ACTION_RECORDING_STARTED = "me.proton.jobforandroid.fitliroutegps.action.TRACK_STARTED"
        const val ACTION_RECORDING_FAILED = "me.proton.jobforandroid.fitliroutegps.action.TRACK_START_FAILED"

        private const val TAG = "LocationService"
        private const val DIAGNOSTIC_TAG = "GPS_DIAG"
        private const val NOTIFICATION_ID = 99
        private const val PREFS_NAME = "active_location_track"
        private const val KEY_HAS_ACTIVE_TRACK = "has_active_track"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_GEO_POINTS = "geo_points"
        private const val KEY_ANCHOR_LATITUDE = "anchor_latitude"
        private const val KEY_ANCHOR_LONGITUDE = "anchor_longitude"
        private const val KEY_ANCHOR_ACCURACY = "anchor_accuracy"
        private const val KEY_ANCHOR_ELAPSED_REALTIME = "anchor_elapsed_realtime"

        var isRunning = false
        var startTime = 0L

        fun hasRecoverableTrack(context: Context): Boolean {
            return readSavedTrack(context.applicationContext) != null
        }

        private fun readSavedTrack(context: Context): SavedTrack? {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(KEY_HAS_ACTIVE_TRACK, false)) return null

            val trackStartTime = preferences.getLong(KEY_START_TIME, 0L)
            val trackDistance = preferences.getFloat(KEY_DISTANCE, Float.NaN)
            if (trackStartTime <= 0L || !trackDistance.isFinite() || trackDistance < 0f) return null

            val points = ArrayList<GeoPoint>()
            val encodedPoints = preferences.getString(KEY_GEO_POINTS, null) ?: return null
            for (encodedPoint in encodedPoints.split('/')) {
                if (encodedPoint.isBlank()) continue
                val coordinates = encodedPoint.split(',')
                if (coordinates.size != 2) return null
                val latitude = coordinates[0].toDoubleOrNull() ?: return null
                val longitude = coordinates[1].toDoubleOrNull() ?: return null
                if (!isValidCoordinate(latitude, longitude)) return null
                points.add(GeoPoint(latitude, longitude))
            }
            if (points.isEmpty()) return null

            val hasAnchorData = preferences.contains(KEY_ANCHOR_LATITUDE) &&
                preferences.contains(KEY_ANCHOR_LONGITUDE) &&
                preferences.contains(KEY_ANCHOR_ACCURACY) &&
                preferences.contains(KEY_ANCHOR_ELAPSED_REALTIME)
            val anchor = if (hasAnchorData) {
                val latitude = preferences.getString(KEY_ANCHOR_LATITUDE, null)?.toDoubleOrNull()
                    ?: return null
                val longitude = preferences.getString(KEY_ANCHOR_LONGITUDE, null)?.toDoubleOrNull()
                    ?: return null
                val accuracy = preferences.getFloat(KEY_ANCHOR_ACCURACY, Float.NaN)
                val elapsedRealtime = preferences.getLong(KEY_ANCHOR_ELAPSED_REALTIME, 0L)
                if (!isValidCoordinate(latitude, longitude) || !accuracy.isFinite() ||
                    accuracy !in 0f..MAX_LOCATION_ACCURACY_METERS || elapsedRealtime <= 0L
                ) return null

                Location("FitLiRouteGPS.restore").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    this.accuracy = accuracy
                    elapsedRealtimeNanos = elapsedRealtime
                }
            } else {
                Location("FitLiRouteGPS.legacy").apply {
                    latitude = points.last().latitude
                    longitude = points.last().longitude
                    accuracy = MAX_LOCATION_ACCURACY_METERS
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }

            return SavedTrack(trackStartTime, trackDistance, points, anchor, !hasAnchorData)
        }

        private fun clearSavedTrack(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}
