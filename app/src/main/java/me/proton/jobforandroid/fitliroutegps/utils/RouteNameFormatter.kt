package me.proton.jobforandroid.fitliroutegps.utils

import android.content.Context
import me.proton.jobforandroid.fitliroutegps.R

object RouteNameFormatter {
    fun defaultName(context: Context, recordedDate: String): String {
        val formattedDate = recordedDate.replace('/', '.').replaceFirst(" ", ", ")
        return context.getString(R.string.default_route_name, formattedDate)
    }
}
