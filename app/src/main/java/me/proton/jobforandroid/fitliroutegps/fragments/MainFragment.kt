package me.proton.jobforandroid.fitliroutegps.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
    private var firstStart = true
    private var timer: Timer? = null
    private var startTime = 0L
    private var permissionRequestStarted = false
    private var osmInitialized = false
    private var lastGpsEnabled: Boolean? = null
    private var boundService: LocationService? = null
    private var serviceBound = false
    private var stopping = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as? LocationService.LocalBinder)?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }
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
        setOnClicks()
        checkServiceState()
        updateTime()
        registerLocReceiver()
        locationUpdates()
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
        timer?.cancel()
        timer = Timer()
        startTime = LocationService.startTime
        timer?.schedule(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    model.timeData.value = getCurrentTime()
                }
            }
        }, 1, 1)
    }

    private fun getAverageVelocity(distance: Float): String {
        return String.format(
            "%.1f m/s",
            3.6f * (distance / ((System.currentTimeMillis() - startTime) / 1000.0f))
        )
    }

    private fun getCurrentTime(): String {
        val fragmentContext = context ?: return ""
        val timeLabel = fragmentContext.getString(R.string.tvTime)
        return "$timeLabel ${TimeUtils.getTime(System.currentTimeMillis() - startTime)}"
    }

    private fun geoPointsToString(list: List<GeoPoint>): String {
        val sb = StringBuilder()
        list.forEach {
            sb.append("${it.latitude},${it.longitude}/")
        }
        Log.d("MyLog", "Points: $sb")
        return sb.toString()
    }

    private fun startStopService() {
        if (stopping) return
        if (!isServiceRunning) {
            if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showToast(R.string.show_loc_not_accessed)
                return
            }
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showToast(R.string.location_disabled)
                return
            }
            locationModel = null
            resetTrackUi()
            startLocService()
            bindLocationService()
        } else {
            val snapshot = boundService?.getTrackSnapshot()
            if (snapshot == null) {
                bindLocationService()
                showToast(R.string.show_service_unavailable)
                return
            }
            stopping = true
            unbindLocationService()
            activity?.stopService(Intent(activity, LocationService::class.java))
            binding.fStartStop.setImageResource(R.drawable.ic_play)
            timer?.cancel()
            if (snapshot.geoPointsList.isEmpty()) {
                showToast(R.string.show_track_empty)
                isServiceRunning = false
                stopping = false
                return
            }
            val track = getTrackItem(snapshot)
            DialogManager.showSaveDialog(requireContext(),
                track,
                object : DialogManager.Listener {
                    override fun onClick() {
                        showToast(R.string.show_track_saved)
                        model.insertTrack(track)
                    }
                })
            stopping = false
        }
        isServiceRunning = !isServiceRunning
    }

    private fun resetTrackUi() = with(binding) {
        tvDistance.setText(R.string.waiting_for_gps)
        tvVelosity.text = "${getString(R.string.velocityLabel)} 0.0 km/h"
        tvAveragaVel.text = "${getString(R.string.avVelocityLabel)} 0.0 m/s"
        tvTime.text = "${getString(R.string.tvTime)} 00:00:00:000"
    }

    private fun getTrackItem(snapshot: LocationModel): TrackItem {
        return TrackItem(
            null,
            getCurrentTime(),
            TimeUtils.getDate(),
            String.format("%.1f", snapshot.distance.div(1000)),
            getAverageVelocity(snapshot.distance),
            geoPointsToString(snapshot.geoPointsList),
        )
    }

    private fun checkServiceState() {
        isServiceRunning = LocationService.isRunning
        if (isServiceRunning) {
            binding.fStartStop.setImageResource(R.drawable.ic_stop)
            startTimer()
            bindLocationService()
        }
    }

    private fun bindLocationService() {
        if (!serviceBound && isAdded) {
            serviceBound = requireContext().bindService(
                Intent(requireContext(), LocationService::class.java), serviceConnection, 0
            )
        }
    }

    private fun unbindLocationService() {
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        boundService = null
    }

    override fun onStart() {
        super.onStart()
        if (LocationService.isRunning) bindLocationService()
    }

    override fun onStop() {
        unbindLocationService()
        super.onStop()
    }

    private fun startLocService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(Intent(activity, LocationService::class.java))
        } else {
            activity?.startService(Intent(activity, LocationService::class.java))
        }
        binding.fStartStop.setImageResource(R.drawable.ic_stop)
        LocationService.startTime = System.currentTimeMillis()
        startTimer()
    }

    override fun onResume() {
        super.onResume()
        checkLocPermission()
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
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                checkLocPermission()
            } else {
                showToast(R.string.show_loc_not_accessed)
            }
        }
    }

    private fun checkLocPermission() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (!osmInitialized) {
                initOsm()
                osmInitialized = true
            }
            checkLocationEnabled()
        } else if (!permissionRequestStarted) {
            permissionRequestStarted = true
            pLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun checkLocationEnabled() {
        val lManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = lManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isEnabled && lastGpsEnabled != false) {
            DialogManager.showLocEnabledDialog(
                activity as AppCompatActivity,
                object : DialogManager.Listener {
                    override fun onClick() {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                })
        } else if (isEnabled && lastGpsEnabled == false) {
            showToast(R.string.show_gps_enabled)
        }
        lastGpsEnabled = isEnabled
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, i: Intent?) {
            if (i?.action == LocationService.LOC_MODEL_INTENT) {
                val locModel =
                    i.getSerializableExtra(LocationService.LOC_MODEL_INTENT) as LocationModel
                model.locationUpdates.value = locModel
            }
        }
    }

    private fun registerLocReceiver() {
        val locFilter = IntentFilter(LocationService.LOC_MODEL_INTENT)
        LocalBroadcastManager.getInstance(activity as AppCompatActivity)
            .registerReceiver(receiver, locFilter)
    }

    private fun addPoint(list: List<GeoPoint>) {
        pl?.addPoint(list[list.size - 1])
    }

    private fun fillPolyline(list: List<GeoPoint>) {
        list.forEach {
            pl?.addPoint(it)
        }
    }

    private fun updatePolyline(list: List<GeoPoint>) {
        if (list.size > 1 && firstStart) {
            fillPolyline(list)
            firstStart = false
        } else {
            addPoint(list)
        }
    }

    override fun onDetach() {
        super.onDetach()
        LocalBroadcastManager.getInstance(activity as AppCompatActivity)
            .unregisterReceiver(receiver)
    }

    companion object {
        @JvmStatic
        fun newInstance() = MainFragment()
    }

}
