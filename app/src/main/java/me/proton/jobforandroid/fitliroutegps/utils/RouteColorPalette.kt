package me.proton.jobforandroid.fitliroutegps.utils

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import me.proton.jobforandroid.fitliroutegps.R

/** Maps persisted legacy preference values to the current route palette. */
object RouteColorPalette {
    fun resolve(context: Context, storedValue: String?): Int = when (storedValue?.uppercase() ?: "#FA2C2C") {
        "#FFC107" -> ContextCompat.getColor(context, R.color.route_electric_blue)
        "#FA2C2C" -> ContextCompat.getColor(context, R.color.route_vivid_violet)
        "#4CAF50" -> ContextCompat.getColor(context, R.color.route_deep_teal)
        else -> Color.parseColor(storedValue ?: "#FA2C2C")
    }
}
