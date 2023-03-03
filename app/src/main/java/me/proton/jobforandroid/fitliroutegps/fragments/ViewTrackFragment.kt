package me.proton.jobforandroid.fitliroutegps.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import me.proton.jobforandroid.fitliroutegps.MainApp
import me.proton.jobforandroid.fitliroutegps.databinding.FragmentViewTrackBinding
import me.proton.jobforandroid.fitliroutegps.viewmodel.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline

class ViewTrackFragment : Fragment() {

    private lateinit var binding: FragmentViewTrackBinding
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
    }

    private fun getTrack() = with(binding) {
        model.currentTrack.observe(viewLifecycleOwner) {
            val speed = "Average Speed: ${it.speed} km/h"
            val distance = "Distance: ${it.distance} km"
            val date = "Date: ${it.date}"
            tvData.text = date
            tvTime.text = it.time
            tvAveragaVel.text = speed
            tvDistance.text = distance

            val polyline = getPolyline(it.geoPoints)
            map.overlays.add(polyline)
            goToStartPosition(polyline.actualPoints[0])
        }
    }

    private fun goToStartPosition(startPosition: GeoPoint) {
        binding.map.controller.zoomTo(20.0)
        binding.map.controller.animateTo(startPosition)

    }

    private fun getPolyline(geoPoints: String): Polyline {
        val polyline = Polyline()
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