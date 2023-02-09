package me.proton.jobforandroid.fitliroutegps.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import me.proton.jobforandroid.fitliroutegps.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference, rootKey)
    }

}