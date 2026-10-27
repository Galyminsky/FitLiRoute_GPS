package me.proton.jobforandroid.fitliroutegps.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import me.proton.jobforandroid.fitliroutegps.MainApp
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.FragmentMainBinding
import me.proton.jobforandroid.fitliroutegps.db.TrackItem
import me.proton.jobforandroid.fitliroutegps.location.LocationModel
import me.proton.jobforandroid.fitliroutegps.location.LocationService
import me.proton.jobforandroid.fitliroutegps.utils.DialogManager
import me.proton.jobforandroid.fitliroutegps.utils.TimeUtils
import me.proton.jobforandroid.fitliroutegps.utils.checkPermission
import me.proton.jobforandroid.fitliroutegps.utils.showToast
import me.proton.jobforandroid.fitliroutegps.viewmodel.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*

class MainFragment : Fragment() {

    private var locationModel: LocationModel? = null
    private var pl: Polyline? = null
    private var isServiceRunning = false
    private var lastGpsEnabledState: Boolean? = null
    private var gpsDialog: AlertDialog? = null
    private var isStopPending = false
    private var timer: Timer? = null
    private var startTime = 0L
    lateinit var mLocOverlay: MyLocationNewOverlay
    private lateinit var pLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var binding: FragmentMainBinding

    private lateinit var fragmentContext: Context

    private val model: MainViewModel by activityViewModels {
        MainViewModel.ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingOsm()
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        registerPermissions()
        registerLocReceiver()
        setOnClicks()
        updateTime()
        locationUpdates()
        requestLocationPermissionIfNeeded()
        checkServiceState()
    }

    private fun setOnClicks() = with(binding) {
        val listener = onClicks()
        fStartStop.setOnClickListener(listener)
        fCenter.setOnClickListener(listener)
    }

    private fun onClicks(): OnClickListener {
        return OnClickListener {
            when (it.id) {
                R.id.fStartStop -> startStopService()
                R.id.fCenter -> centerLocation()
            }
        }
    }

    private fun centerLocation() {
        binding.map.controller.animateTo(mLocOverlay.myLocation)
        mLocOverlay.enableFollowLocation()
    }

    private fun locationUpdates() = with(binding) {
        model.locationUpdates.observe(viewLifecycleOwner) {
            Log.d(
                "GPS_DIAG",
                "ui_observer distanceM=${it.distance} speedMps=${it.velocity} points=${it.geoPointsList.size}"
            )
            val distanceLabel = getString(R.string.distanceLabel)
            val velocityLabel = getString(R.string.velocityLabel)
            val avVelocityLabel = getString(R.string.avVelocityLabel)
            val distance = "$distanceLabel ${String.format("%.1f", it.distance)} m"
            val velocity = "$velocityLabel ${String.format("%.1f", 3.6 * it.velocity)} km/h"
            val avVelocity = "$avVelocityLabel ${getAverageVelocity(it.distance)}"

            tvDistance.text = distance
            tvVelosity.text = velocity
            tvAveragaVel.text = avVelocity
            locationModel = it
            updatePolyline(it.geoPointsList)
        }
    }

    private fun updateTime() {
        model.timeData.observe(viewLifecycleOwner) {
            binding.tvTime.text = it
        }
    }

    private fun startTimer() {
        cancelTimer()
        startTime = LocationService.startTime
        val newTimer = Timer()
        timer = newTimer
        newTimer.schedule(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    if (timer === newTimer) {
                        model.timeData.value = getCurrentTime()
                    }
                }
            }
        }, 0, 1_000)
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer?.purge()
        timer = null
    }

    private fun getAverageVelocity(distance: Float): String {
        val elapsedMillis = System.currentTimeMillis() - startTime
        val safeDistance = distance.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val averageVelocity = if (elapsedMillis > 0L) {
            safeDistance / (elapsedMillis / 1000.0f)
        } else {
            0f
        }
        return String.format("%.1f m/s", averageVelocity)
    }

    private fun getCurrentTime(): String {
        val fragmentContext = context ?: return ""
        val timeLabel = fragmentContext.getString(R.string.tvTime)
        return "$timeLabel ${TimeUtils.getTime(System.currentTimeMillis() - startTime)}"
    }

    private fun geoPointsToString(list: List<GeoPoint>): String {
        val sb = StringBuilder()
        getValidGeoPoints(list).forEach {
            sb.append("${it.latitude},${it.longitude}/")
        }
        return sb.toString()
    }

    private fun getValidGeoPoints(list: List<GeoPoint>): List<GeoPoint> {
        return list.filter { point ->
            point.latitude.isFinite() && point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
        }
    }

    private fun startStopService() {
        if (isServiceStarting || isStopPending) return

        if (!isServiceRunning) {
            if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                pLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                return
            }
            if (!isGpsEnabled()) {
                checkLocationEnabled()
                return
            }
            startLocService(LocationService.hasRecoverableTrack(requireContext()))
        } else {
            isStopPending = true
            binding.fStartStop.isEnabled = false
            try {
                activity?.startService(
                    Intent(activity, LocationService::class.java)
                        .setAction(LocationService.ACTION_STOP)
                )
            } catch (error: RuntimeException) {
                isStopPending = false
                binding.fStartStop.isEnabled = true
                Log.e("MainFragment", "Unable to stop location service", error)
            }
        }
    }

    private fun getTrackItem(): TrackItem {
        return TrackItem(
            null,
            getCurrentTime(),
            TimeUtils.getDate(),
            String.format("%.1f", locationModel?.distance?.div(1000) ?: 0.0f),
            getAverageVelocity(locationModel?.distance ?: 0.0f),
            geoPointsToString(locationModel?.geoPointsList ?: listOf()),
        )
    }

    private fun finishSavingStoppedTrack(finalModel: LocationModel?) {
        isStopPending = false
        isServiceRunning = false
        binding.fStartStop.setImageResource(R.drawable.ic_play)
        binding.fStartStop.isEnabled = true
        cancelTimer()
        if (finalModel != null) {
            locationModel = finalModel
            model.locationUpdates.value = finalModel
        }
        val track = getTrackItem()
        val validPoints = getValidGeoPoints(locationModel?.geoPointsList ?: emptyList())
        val elapsedMillis = System.currentTimeMillis() - startTime
        if (validPoints.isEmpty() || elapsedMillis <= 0L) {
            showToast(R.string.show_loc_not_accessed)
            return
        }
        DialogManager.showSaveDialog(requireContext(), track, object : DialogManager.Listener {
            override fun onClick() {
                model.insertTrack(track) { saved ->
                    if (isAdded) showToast(
                        if (saved) R.string.show_track_saved else R.string.show_track_save_failed
                    )
                }
            }
        })
    }

    private fun checkServiceState() {
        isServiceRunning = LocationService.isRunning
        if (isServiceRunning) {
            binding.fStartStop.setImageResource(R.drawable.ic_stop)
            binding.fStartStop.isEnabled = true
            startTimer()
        }
    }

    private var isServiceStarting = false

    private fun startLocService(resume: Boolean) {
        val currentActivity = activity ?: return
        if (isServiceStarting || isServiceRunning) return

        if (!resume) {
            locationModel = null
            pl?.setPoints(emptyList())
        }

        isServiceStarting = true
        binding.fStartStop.isEnabled = false
        val intent = Intent(currentActivity, LocationService::class.java).setAction(
            if (resume) LocationService.ACTION_RESUME else LocationService.ACTION_START
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentActivity.startForegroundService(intent)
            } else {
                currentActivity.startService(intent)
            }
        } catch (error: RuntimeException) {
            Log.e("MainFragment", "Unable to start location service", error)
            onLocationServiceStartFailed()
        }
    }

    private fun onLocationServiceStartFailed() {
        isServiceStarting = false
        isServiceRunning = false
        binding.fStartStop.isEnabled = true
        binding.fStartStop.setImageResource(R.drawable.ic_play)
        cancelTimer()
        showToast(R.string.show_loc_not_accessed)
    }

    private fun isGpsEnabled(): Boolean {
        val currentActivity = activity ?: return false
        val locationManager =
            currentActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    override fun onResume() {
        super.onResume()
        if (view != null && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            checkLocationEnabled()
        }
    }

    override fun onDestroyView() {
        cancelTimer()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
        gpsDialog?.dismiss()
        gpsDialog = null
        super.onDestroyView()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentContext = context
    }

    private fun settingOsm() {
        Configuration.getInstance().load(
            activity as AppCompatActivity,
            activity?.getSharedPreferences("osm_pref", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = BuildConfig.LIBRARY_PACKAGE_NAME
    }

    private fun initOsm() = with(binding) {
        pl = Polyline()
        pl?.outlinePaint?.color = Color.parseColor(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("color_key", "#FA2C2C")
        )
        map.controller.setZoom(18.0)
        //map.controller.animateTo(GeoPoint(52.54735, 62.50001))
        val mLocProvider = GpsMyLocationProvider(activity)
        mLocOverlay = MyLocationNewOverlay(mLocProvider, map)
        mLocOverlay.enableMyLocation()
        mLocOverlay.enableFollowLocation()
        mLocOverlay.runOnFirstFix {
            map.overlays.clear()
            map.overlays.add(mLocOverlay)
            map.overlays.add(pl)
        }
    }

    private fun registerPermissions() {
        pLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                if (view != null && isAdded) {
                    initOsm()
                    checkLocationEnabled()
                }
            } else {
                if (view != null && isAdded) {
                    showToast(R.string.show_loc_not_accessed)
                }
            }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            initOsm()
            checkLocationEnabled()
        } else {
            pLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationEnabled() {
        val currentActivity = activity ?: return
        val lManager = currentActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = lManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val wasEnabled = lastGpsEnabledState
        lastGpsEnabledState = isEnabled

        if (!isEnabled) {
            if (gpsDialog?.isShowing != true && view != null) {
                gpsDialog = DialogManager.showLocEnabledDialog(
                    currentActivity,
                    object : DialogManager.Listener {
                        override fun onClick() {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    }
                )
            }
        } else {
            gpsDialog?.dismiss()
            gpsDialog = null
            if (wasEnabled == false) {
                showToast(R.string.show_gps_enabled)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, i: Intent?) {
            if (i?.action == LocationService.LOC_MODEL_INTENT) {
                val locModel =
                    i.getSerializableExtra(LocationService.LOC_MODEL_INTENT) as LocationModel
                Log.d(
                    "GPS_DIAG",
                    "ui_broadcast_received distanceM=${locModel.distance} " +
                        "speedMps=${locModel.velocity} points=${locModel.geoPointsList.size}"
                )
                model.locationUpdates.value = locModel
            } else if (i?.action == LocationService.ACTION_RECORDING_STARTED) {
                isServiceStarting = false
                isServiceRunning = true
                binding.fStartStop.isEnabled = true
                binding.fStartStop.setImageResource(R.drawable.ic_stop)
                startTimer()
            } else if (i?.action == LocationService.ACTION_RECORDING_FAILED) {
                onLocationServiceStartFailed()
            } else if (i?.action == LocationService.ACTION_RECORDING_STOPPED) {
                val finalModel = i.getSerializableExtra(LocationService.LOC_MODEL_INTENT) as? LocationModel
                finishSavingStoppedTrack(finalModel)
            }
        }
    }

    private fun registerLocReceiver() {
        val locFilter = IntentFilter().apply {
            addAction(LocationService.LOC_MODEL_INTENT)
            addAction(LocationService.ACTION_RECORDING_STARTED)
            addAction(LocationService.ACTION_RECORDING_STOPPED)
            addAction(LocationService.ACTION_RECORDING_FAILED)
        }
        LocalBroadcastManager.getInstance(activity as AppCompatActivity)
            .registerReceiver(receiver, locFilter)
    }

    private fun updatePolyline(list: List<GeoPoint>) {
        pl?.setPoints(list)
    }

    companion object {
        @JvmStatic
        fun newInstance() = MainFragment()
    }

}
