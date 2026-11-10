package me.proton.jobforandroid.fitliroutegps.utils

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.SaveDialogBinding
import me.proton.jobforandroid.fitliroutegps.db.TrackItem
import me.proton.jobforandroid.fitliroutegps.utils.RouteNameFormatter

object DialogManager {

    fun showLocEnabledDialog(context: Context, listener: Listener) {
        MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_FitRoute_ChoiceDialog)
            .setTitle(R.string.location_disabled)
            .setMessage(R.string.location_dialog_message)
            .setPositiveButton(R.string.location_bnt_yes) { _, _ -> listener.onClick() }
            .setNegativeButton(R.string.location_bnt_no, null)
            .show()
    }

    fun showSaveDialog(context: Context, item: TrackItem, onSave: (String) -> Unit, onCancel: () -> Unit = {}) {
        val binding = SaveDialogBinding.inflate(LayoutInflater.from(context), null, false)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        binding.apply {

            val time = item.time.substringAfter(':').trim()
            val velocity = SpeedUnitFormatter.kmh(item.speed, context.getString(R.string.speed_unit_kmh))
            val distance = "${item.distance} km"
            val defaultName = RouteNameFormatter.defaultName(context, item.date)

            tvTimeDialog.text = time
            tvVelocityDialog.text = velocity
            tvDistanceDialog.text = distance
            routeNameInput.setText(defaultName)

            btnSaveDialog.setOnClickListener {
                onSave(routeNameInput.text.toString().trim().ifEmpty { defaultName })
                dialog.dismiss()
            }
            btnCancelDialog.setOnClickListener {
                onCancel()
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    interface Listener {
        fun onClick()
    }
}
