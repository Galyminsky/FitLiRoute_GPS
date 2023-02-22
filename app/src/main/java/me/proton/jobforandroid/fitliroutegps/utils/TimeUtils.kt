package me.proton.jobforandroid.fitliroutegps.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

object TimeUtils {

    @SuppressLint("SimpleDateFormat")
    private val timeFormatter = SimpleDateFormat("HH:mm:ss")

    fun getTime(timeInMillis: Long): String {
        val cv = Calendar.getInstance()
        timeFormatter.timeZone = TimeZone.getTimeZone("UTC")
        cv.timeInMillis = timeInMillis
        return timeFormatter.format(cv.time)
    }
}