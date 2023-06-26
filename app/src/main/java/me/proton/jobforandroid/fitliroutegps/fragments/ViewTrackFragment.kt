package me.proton.jobforandroid.fitliroutegps.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import me.proton.jobforandroid.fitliroutegps.MainApp
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.FragmentViewTrackBinding
import me.proton.jobforandroid.fitliroutegps.viewmodel.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class ViewTrackFragment : Fragment() {

    private lateinit var binding: FragmentViewTrackBinding
    private var startPoint: GeoPoint? = null
    private val model: MainViewModel by activityViewModels {
        MainViewModel.ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingOsm()
        binding = FragmentViewTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getTrack()
        binding.fCenter.setOnClickListener {
            if (startPoint != null) binding.map.controller.animateTo(startPoint)
        }
    }

    private fun getTrack() = with(binding) {
        model.currentTrack.observe(viewLifecycleOwner) {
            val speed = resources.getString(R.string.fvt_average_velosity) + it.speed
            val distance = resources.getString(R.string.fvt_distance) + it.distance
            val date = resources.getString(R.string.fvt_date) + it.date
            tvData.text = date
            tvTime.text = it.time
            tvAveragaVel.text = speed
            tvDistance.text = distance

            val polyline = getPolyline(it.geoPoints)
            map.overlays.add(polyline)
            setMarkers(polyline.actualPoints)
            goToStartPosition(polyline.actualPoints[0])
            startPoint = polyline.actualPoints[0]
        }
    }

    private fun goToStartPosition(startPosition: GeoPoint) {
        binding.map.controller.zoomTo(18.0)
        binding.map.controller.animateTo(startPosition)
    }

    private fun setMarkers(list: List<GeoPoint>) = with(binding) {
        val startMarker = Marker(map)
        val finishMarker = Marker(map)
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        finishMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.icon = getDrawable(requireContext(), R.drawable.ic_loc_start)
        finishMarker.icon = getDrawable(requireContext(), R.drawable.ic_loc_finish)
        startMarker.position = list[0]
        finishMarker.position = list[list.size - 1]
        map.overlays.add(startMarker)
        map.overlays.add(finishMarker)
    }

    private fun getPolyline(geoPoints: String): Polyline {
        val polyline = Polyline()
        polyline.outlinePaint.color = Color.parseColor(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("color_key", "#FA2C2C")
        )
        val list = geoPoints.split("/")
        list.forEach {
            if (it.isEmpty()) return@forEach
            val points = it.split(",")
            polyline.addPoint(GeoPoint(points[0].toDouble(), points[1].toDouble()))
        }
        return polyline
    }

    private fun settingOsm() {
        Configuration.getInstance().load(
            activity as AppCompatActivity,
            activity?.getSharedPreferences("osm_pref", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = BuildConfig.LIBRARY_PACKAGE_NAME
    }

    companion object {
        @JvmStatic
        fun newInstance() = ViewTrackFragment()
    }
}