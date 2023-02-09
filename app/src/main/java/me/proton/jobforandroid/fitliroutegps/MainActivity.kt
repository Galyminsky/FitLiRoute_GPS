package me.proton.jobforandroid.fitliroutegps

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import me.proton.jobforandroid.fitliroutegps.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBottomNavClicks()

    }

    private fun onBottomNavClicks() {
        binding.bNan.setOnItemSelectedListener {
            when(it.itemId) {

                R.id.home_menu -> {
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                }
                R.id.trucks_menu -> {
                    Toast.makeText(this, "Trucks", Toast.LENGTH_SHORT).show()
                }
                R.id.setting_menu -> {
                    Toast.makeText(this, "Setting", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
    }
}