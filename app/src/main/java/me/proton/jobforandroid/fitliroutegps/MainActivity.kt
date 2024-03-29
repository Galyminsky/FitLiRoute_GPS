package me.proton.jobforandroid.fitliroutegps

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import me.proton.jobforandroid.fitliroutegps.databinding.ActivityMainBinding
import me.proton.jobforandroid.fitliroutegps.fragments.MainFragment
import me.proton.jobforandroid.fitliroutegps.fragments.SettingsFragment
import me.proton.jobforandroid.fitliroutegps.fragments.TracksFragment
import me.proton.jobforandroid.fitliroutegps.utils.openFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var analytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        analytics = Firebase.analytics
        onBottomNavClicks()
        openFragment(MainFragment.newInstance())
    }

    private fun onBottomNavClicks() {
        binding.bNan.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home_menu -> {
                    openFragment(MainFragment.newInstance())
                }

                R.id.trucks_menu -> {
                    openFragment(TracksFragment.newInstance())
                }

                R.id.setting_menu -> {
                    openFragment(SettingsFragment())
                }
            }
            true
        }
    }
}