package me.proton.jobforandroid.fitliroutegps.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.DialogLegalDocumentBinding
import me.proton.jobforandroid.fitliroutegps.databinding.FragmentSettingsBinding
import me.proton.jobforandroid.fitliroutegps.utils.LegalMarkdownFormatter
import me.proton.jobforandroid.fitliroutegps.utils.RouteColorPalette

class SettingsFragment : PreferenceFragmentCompat() {
    private var settingsBinding: FragmentSettingsBinding? = null
    private var legalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val preferences = super.onCreateView(inflater, container, savedInstanceState)
        val binding = FragmentSettingsBinding.inflate(inflater, container, false)
        binding.preferenceContainer.addView(preferences,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        settingsBinding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(resources.getColor(R.color.design_background, null))
        listView.clipToPadding = false
        listView.setPadding(0, 0, 0, 0)
        val binding = settingsBinding ?: return
        binding.privacyPolicyLink.setOnClickListener {
            showLegalDocument("privacy-policy", R.string.privacy_policy_title)
        }
        binding.termsOfUseLink.setOnClickListener {
            showLegalDocument("terms", R.string.terms_of_use_title)
        }
        legalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { positionLegalLinks() }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(legalLayoutListener)
    }

    override fun onDestroyView() {
        legalLayoutListener?.let { settingsBinding?.root?.viewTreeObserver?.removeOnGlobalLayoutListener(it) }
        legalLayoutListener = null
        settingsBinding = null
        super.onDestroyView()
    }

    private fun positionLegalLinks() {
        val binding = settingsBinding ?: return
        val navBar = activity?.findViewById<View>(R.id.bNan) ?: return
        if (binding.root.height == 0 || navBar.height == 0) return
        val rootPosition = IntArray(2)
        val navPosition = IntArray(2)
        binding.root.getLocationOnScreen(rootPosition)
        navBar.getLocationOnScreen(navPosition)
        val gap = (20 * resources.displayMetrics.density).toInt()
        val rootBottom = rootPosition[1] + binding.root.height
        val bottomMargin = (rootBottom - navPosition[1] + gap).coerceAtLeast(0)
        val params = binding.legalLinks.layoutParams as ConstraintLayout.LayoutParams
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin
            binding.legalLinks.layoutParams = params
        }
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

    private fun showLegalDocument(name: String, titleId: Int) {
        val language = if (resources.configuration.locales[0].language == "ru") "ru" else "en"
        val markdown = requireContext().assets.open("policies/$name-$language.md")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val binding = DialogLegalDocumentBinding.inflate(layoutInflater)
        binding.documentTitle.setText(titleId)
        binding.documentBody.text = LegalMarkdownFormatter.format(markdown)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FitRoute_ChoiceDialog)
            .setView(binding.root)
            .create()
        binding.closeDocument.setOnClickListener { dialog.dismiss() }
        dialog.show()
        val density = resources.displayMetrics.density
        val width = minOf(resources.displayMetrics.widthPixels - (32 * density).toInt(),
            (560 * density).toInt())
        val height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        dialog.window?.setLayout(width, height)
    }
}
