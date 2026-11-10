package me.proton.jobforandroid.fitliroutegps.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import me.proton.jobforandroid.fitliroutegps.R
import me.proton.jobforandroid.fitliroutegps.databinding.SaveDialogBinding
import me.proton.jobforandroid.fitliroutegps.db.TrackItem
import me.proton.jobforandroid.fitliroutegps.utils.RouteNameFormatter

object DialogManager {

    fun showLocEnabledDialog(context: Context, listener: Listener) {
        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()
        dialog.setTitle(R.string.location_disabled)
        dialog.setMessage(context.getString(R.string.location_dialog_message))
        dialog.setButton(
            AlertDialog.BUTTON_POSITIVE,
            context.getString(R.string.location_bnt_yes)
        ) { _, _ ->
            listener.onClick()

        }

        dialog.setButton(
            AlertDialog.BUTTON_NEGATIVE,
            context.getString(R.string.location_bnt_no)
        ) { _, _ ->
            dialog.dismiss()

        }
        dialog.show()
    }

    fun showSaveDialog(context: Context, item: TrackItem, onSave: (String) -> Unit, onCancel: () -> Unit = {}) {
        val binding = SaveDialogBinding.inflate(LayoutInflater.from(context), null, false)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        binding.apply {

            val time = item.time.substringAfter(':').trim()
            val velocity = item.speed.replace("m/s", "km/h")
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
