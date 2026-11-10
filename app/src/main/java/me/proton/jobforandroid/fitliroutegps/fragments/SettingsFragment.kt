package me.proton.jobforandroid.fitliroutegps.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.utils.RouteColorPalette

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference !is ListPreference) {
            super.onDisplayPreferenceDialog(preference)
            return
        }
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FitRoute_ChoiceDialog)
            .setTitle(preference.title)
            .setSingleChoiceItems(preference.entries, preference.findIndexOfValue(preference.value)) { dialog, index ->
                val chosen = preference.entryValues[index].toString()
                if (preference.callChangeListener(chosen)) preference.value = chosen
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private lateinit var timePref: Preference
    private lateinit var colorPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference, rootKey)
        init()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(resources.getColor(R.color.design_background, null))
        listView.clipToPadding = false
        listView.setPadding(0, 0, 0, 106)
    }

    private fun init() {
        timePref = findPreference("update_time_key")!!
        colorPref = findPreference("color_key")!!
        val changeListener = onChangeListener()
        timePref.onPreferenceChangeListener = changeListener
        colorPref.onPreferenceChangeListener = changeListener
        initPref()
    }

    private fun onChangeListener(): Preference.OnPreferenceChangeListener {
        return Preference.OnPreferenceChangeListener { pref, value ->
            when (pref.key) {
                "update_time_key" -> onTimeChange(value.toString())
                "color_key" -> {
                    pref.icon?.setTint(RouteColorPalette.resolve(requireContext(), value.toString()))
                    val values = resources.getStringArray(R.array.color_value)
                    val names = resources.getStringArray(R.array.color_name)
                    pref.summary = names[values.indexOf(value.toString())]
                }
            }
            true
        }
    }

    private fun onTimeChange(value: String) {
        val nameArray = resources.getStringArray(R.array.loc_time_update_name)
        val valueArray = resources.getStringArray(R.array.loc_time_update_value)
        timePref.summary = nameArray[valueArray.indexOf(value)]
    }

    private fun initPref() {
        val pref = timePref.preferenceManager.sharedPreferences
        val nameArray = resources.getStringArray(R.array.loc_time_update_name)
        val valueArray = resources.getStringArray(R.array.loc_time_update_value)
        timePref.summary = nameArray[valueArray.indexOf(pref?.getString("update_time_key", "3000"))]

        val trackColor = pref?.getString("color_key", "#FA2C2C")
        colorPref.icon?.setTint(RouteColorPalette.resolve(requireContext(), trackColor))
        val colorValues = resources.getStringArray(R.array.color_value)
        colorPref.summary = resources.getStringArray(R.array.color_name)[colorValues.indexOf(trackColor)]
    }
}
