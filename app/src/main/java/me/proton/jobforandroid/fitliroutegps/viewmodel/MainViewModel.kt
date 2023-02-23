package me.proton.jobforandroid.fitliroutegps.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import me.proton.jobforandroid.fitliroutegps.location.LocationModel

class MainViewModel : ViewModel() {

    val locationUpdates = MutableLiveData<LocationModel>()
    val timeData = MutableLiveData<String>()

}