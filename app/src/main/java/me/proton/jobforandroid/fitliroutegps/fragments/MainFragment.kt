package me.proton.jobforandroid.fitliroutegps.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.res.ColorStateList
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import me.proton.jobforandroid.fitliroutegps.MainApp
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.FragmentMainBinding
import me.proton.jobforandroid.fitliroutegps.db.TrackItem
import me.proton.jobforandroid.fitliroutegps.location.LocationModel
import me.proton.jobforandroid.fitliroutegps.location.LocationService
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftStore
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftRecovery
import me.proton.jobforandroid.fitliroutegps.utils.DialogManager
import me.proton.jobforandroid.fitliroutegps.utils.TimeUtils
import me.proton.jobforandroid.fitliroutegps.utils.RouteColorPalette
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : Fragment() {

    private var locationModel: LocationModel? = null
    private var pl: Polyline? = null
    private var isServiceRunning = false
    private var firstStart = true
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var timerBaseRealtime = 0L
    private var timerInitialElapsed = 0L
    private var startTime = 0L
    private var permissionRequestStarted = false
    private var osmInitialized = false
    private var lastGpsEnabled: Boolean? = null
    private var boundService: LocationService? = null
    private var serviceBound = false
    private var stopping = false
    private var savingDraft = false
    private var draftChecked = false
    private var pendingDraft: TrackDraft? = null
    private var draftDialogVisible = false
    private val draftStore by lazy { TrackDraftStore(requireContext().applicationContext) }
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
        binding.map.setBuiltInZoomControls(false)
        binding.zoomIn.setOnClickListener { binding.map.controller.zoomIn() }
        binding.zoomOut.setOnClickListener { binding.map.controller.zoomOut() }
        checkServiceState()
        updateTime()
        registerLocReceiver()
        locationUpdates()
        checkForDraft()
        updateGpsStatus()
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
        if (!::mLocOverlay.isInitialized) return
        val position = mLocOverlay.myLocation ?: return
        binding.map.controller.animateTo(position)
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

            tvDistance.text = String.format(Locale.getDefault(), "%.2f", it.distance / 1000f)
            tvVelosity.text = velocity.substringAfter(':').trim()
            tvAveragaVel.text = avVelocity.substringAfter(':').trim().replace("m/s", "km/h")
            locationModel = it
            updatePolyline(it.geoPointsList)
        }
    }

    private fun updateTime() {
        model.timeData.observe(viewLifecycleOwner) {
            binding.tvTime.text = it.substringAfter(':').trim()
        }
    }

    private fun startTimer() {
        stopTimer()
        startTime = LocationService.startTime
        timerInitialElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
        timerBaseRealtime = SystemClock.elapsedRealtime()
        val update = object : Runnable {
            override fun run() {
                val elapsed = timerInitialElapsed + SystemClock.elapsedRealtime() - timerBaseRealtime
                model.timeData.value = getCurrentTime(elapsed)
                timerHandler.postDelayed(this, 1000L - elapsed % 1000L)
            }
        }
        timerRunnable = update
        timerHandler.post(update)
    }

    private fun stopTimer() {
        timerRunnable?.let(timerHandler::removeCallbacks)
        timerRunnable = null
    }

    private fun getAverageVelocity(distance: Float): String {
        return String.format(
            "%.1f m/s",
            3.6f * (distance / ((System.currentTimeMillis() - startTime) / 1000.0f))
        )
    }

    private fun getCurrentTime(elapsedMillis: Long): String {
        val fragmentContext = context ?: return ""
        val timeLabel = fragmentContext.getString(R.string.tvTime)
        return "$timeLabel ${TimeUtils.getTime(elapsedMillis)}"
    }

    private fun geoPointsToString(list: List<GeoPoint>): String {
        val sb = StringBuilder()
        list.forEach {
            sb.append("${it.latitude},${it.longitude}/")
        }
        return sb.toString()
    }

    private fun startStopService() {
        if (stopping || savingDraft) return
        if (!isServiceRunning) {
            if (pendingDraft != null) {
                showDraftChoices(pendingDraft!!)
                return
            }
            if (!draftChecked) {
                showToast(R.string.show_draft_pending)
                return
            }
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
            val service = boundService
            if (service == null) {
                bindLocationService()
                showToast(R.string.show_service_unavailable)
                return
            }
            stopping = true
            viewLifecycleOwner.lifecycleScope.launch {
                val appContext = requireContext().applicationContext
                val draft = withContext(NonCancellable) {
                    service.checkpointForStop()?.also {
                        appContext.stopService(Intent(appContext, LocationService::class.java))
                    }
                }
                if (draft == null) {
                    stopping = false
                    showToast(R.string.show_service_unavailable)
                    return@launch
                }
                pendingDraft = draft
                unbindLocationService()
                binding.fStartStop.setImageResource(R.drawable.ic_play)
                stopTimer()
                isServiceRunning = false
                stopping = false
                if (draft.points.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setMessage(R.string.show_track_empty)
                        .setPositiveButton(R.string.draft_delete) { _, _ -> discardDraft() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    showSaveDraft(draft)
                }
            }
            return
        }
        isServiceRunning = !isServiceRunning
    }

    private fun showSaveDraft(draft: TrackDraft) {
        val track = getTrackItem(draft)
        DialogManager.showSaveDialog(requireContext(), track, { name ->
                if (savingDraft) return@showSaveDialog
                savingDraft = true
                viewLifecycleOwner.lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        try { model.saveRecoveredTrack(track.copy(name = name)) } catch (_: Exception) { false }
                    }
                    val draftDeleted = if (saved) withContext(Dispatchers.IO) {
                        try { draftStore.delete(); true } catch (_: Exception) { false }
                    } else false
                    savingDraft = false
                    if (saved) {
                        if (draftDeleted) pendingDraft = null
                        showToast(if (draftDeleted) R.string.show_track_saved else R.string.show_draft_cleanup_failed)
                    } else {
                        showToast(R.string.show_draft_save_failed)
                        showDraftChoices(draft)
                    }
                }
        }) { discardDraft() }
    }

    private fun discardDraft() {
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                try { draftStore.delete(); true } catch (_: Exception) { false }
            }
            if (deleted) {
                pendingDraft = null
                draftChecked = true
            } else showToast(R.string.show_draft_save_failed)
        }
    }

    private fun checkForDraft() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { draftStore.read() } }
            if (result.isFailure) {
                draftChecked = false
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.draft_read_failed)
                    .setPositiveButton(R.string.draft_delete) { _, _ -> discardDraft() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                return@launch
            }
            val draft = result.getOrNull()
            draftChecked = true
            if (draft == null || LocationService.isRunning) return@launch
            pendingDraft = draft
            showDraftChoices(draft)
        }
    }

    private fun showDraftChoices(draft: TrackDraft) {
        if (draftDialogVisible) return
        draftDialogVisible = true
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.draft_found)
                .setItems(if (draft.points.isEmpty()) R.array.draft_empty_actions else R.array.draft_actions) { _, choice ->
                    when (choice) {
                        0 -> resumeDraft(draft)
                        1 -> if (draft.points.isEmpty()) discardDraft() else showSaveDraft(draft)
                        2 -> discardDraft()
                    }
                }.create().also { dialog ->
                    dialog.setOnDismissListener { draftDialogVisible = false }
                    dialog.show()
                }
    }

    private fun resumeDraft(draft: TrackDraft) {
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showToast(R.string.show_loc_not_accessed)
            return
        }
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showToast(R.string.location_disabled)
            return
        }
        LocationService.startTime = TrackDraftRecovery.resumedStartTime(draft, System.currentTimeMillis())
        startLocService(LocationService.ACTION_RESUME)
        bindLocationService()
        pendingDraft = null
        isServiceRunning = true
    }

    private fun resetTrackUi() = with(binding) {
        tvDistance.setText(R.string.waiting_for_gps)
        tvVelosity.text = "0.0 km/h"
        tvAveragaVel.text = "0.0 m/s"
        tvTime.text = "00:00:00"
    }

    private fun getTrackItem(draft: TrackDraft): TrackItem {
        val duration = (draft.updatedAt - draft.startTime).coerceAtLeast(1L)
        return TrackItem(
            draft.id,
            "${getString(R.string.tvTime)} ${TimeUtils.getTime(duration)}",
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(draft.updatedAt)),
            String.format("%.1f", draft.distance.div(1000)),
            String.format("%.1f m/s", 3.6f * (draft.distance / (duration / 1000.0f))),
            geoPointsToString(draft.points.map { GeoPoint(it.latitude, it.longitude) }),
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
        if (LocationService.isRunning) {
            bindLocationService()
            startTimer()
        }
    }

    override fun onStop() {
        stopTimer()
        unbindLocationService()
        super.onStop()
    }

    override fun onDestroyView() {
        stopTimer()
        super.onDestroyView()
    }

    private fun startLocService(action: String = LocationService.ACTION_NEW) {
        val intent = Intent(activity, LocationService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(intent)
        } else {
            activity?.startService(intent)
        }
        binding.fStartStop.setImageResource(R.drawable.ic_stop)
        if (action == LocationService.ACTION_NEW) LocationService.startTime = System.currentTimeMillis()
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
        pl?.outlinePaint?.color = RouteColorPalette.resolve(requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("color_key", "#FA2C2C")
        )
        map.controller.setZoom(18.0)
        //map.controller.animateTo(GeoPoint(52.54735, 62.50001))
        val mLocProvider = GpsMyLocationProvider(activity)
        mLocOverlay = MyLocationNewOverlay(mLocProvider, map)
        val marker = AppCompatResources.getDrawable(requireContext(), R.drawable.current_location_dot)!!
        val markerSize = (60 * resources.displayMetrics.density).toInt()
        val markerBitmap = Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888)
        marker.setBounds(0, 0, markerSize, markerSize)
        marker.draw(Canvas(markerBitmap))
        mLocOverlay.setPersonIcon(markerBitmap)
        mLocOverlay.setDirectionIcon(markerBitmap)
        mLocOverlay.setPersonAnchor(.5f, .5f)
        mLocOverlay.setDirectionAnchor(.5f, .5f)
        mLocOverlay.enableMyLocation()
        mLocOverlay.enableFollowLocation()
        mLocOverlay.runOnFirstFix {
            activity?.runOnUiThread {
                if (view == null || !isAdded || !::mLocOverlay.isInitialized) return@runOnUiThread
                map.overlays.clear()
                map.overlays.add(mLocOverlay)
                pl?.let(map.overlays::add)
            }
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
        updateGpsStatus(isEnabled)
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

    private fun updateGpsStatus(enabled: Boolean = (requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        val color = Color.parseColor(if (enabled) "#2E9A65" else "#C1654B")
        binding.gpsDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.gpsStatus.setText(if (enabled) R.string.gps_status_enabled else R.string.gps_status_disabled)
        binding.gpsStatus.setTextColor(color)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, i: Intent?) {
            if (i?.action == LocationService.ACTION_START_FAILED) {
                stopTimer()
                unbindLocationService()
                isServiceRunning = false
                binding.fStartStop.setImageResource(R.drawable.ic_play)
                showToast(R.string.show_service_unavailable)
                checkForDraft()
                return
            }
            if (i?.action == LocationService.LOC_MODEL_INTENT) {
                val locModel =
                    i.getSerializableExtra(LocationService.LOC_MODEL_INTENT) as LocationModel
                model.locationUpdates.value = locModel
            }
        }
    }

    private fun registerLocReceiver() {
        val locFilter = IntentFilter(LocationService.LOC_MODEL_INTENT)
        locFilter.addAction(LocationService.ACTION_START_FAILED)
        LocalBroadcastManager.getInstance(activity as AppCompatActivity)
            .registerReceiver(receiver, locFilter)
    }

    private fun addPoint(list: List<GeoPoint>) {
        if (list.isNotEmpty()) pl?.addPoint(list.last())
    }

    private fun fillPolyline(list: List<GeoPoint>) {
        list.forEach {
            pl?.addPoint(it)
        }
    }

    private fun updatePolyline(list: List<GeoPoint>) {
        if (list.isEmpty() || pl == null) return
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
