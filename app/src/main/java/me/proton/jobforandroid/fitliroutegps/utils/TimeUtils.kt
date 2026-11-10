package me.proton.jobforandroid.fitliroutegps.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
object TimeUtils {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm")

    fun getTime(timeInMillis: Long): String {
        val seconds = timeInMillis.coerceAtLeast(0L) / 1000L
        val hours = seconds / 3600L
        val minutes = seconds % 3600L / 60L
        val remainingSeconds = seconds % 60L
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    }

    fun getDate(): String {
        val cv = Calendar.getInstance()
        return dateFormatter.format(cv.time)
    }
}
