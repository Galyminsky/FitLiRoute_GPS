package me.proton.jobforandroid.fitliroutegps.viewmodel

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import me.proton.jobforandroid.fitliroutegps.db.MainDB
import me.proton.jobforandroid.fitliroutegps.db.TrackItem
import me.proton.jobforandroid.fitliroutegps.location.LocationModel

@Suppress("UNCHECKED_CAST")
class MainViewModel(db: MainDB) : ViewModel() {

    val dao = db.getDao()

    val locationUpdates = MutableLiveData<LocationModel>()
    val currentTrack = MutableLiveData<TrackItem>()
    val timeData = MutableLiveData<String>()
    val tracks = dao.getAllTracks().asLiveData()

    fun insertTrack(trackItem: TrackItem) = viewModelScope.launch {
        dao.insertTrack(trackItem)
    }

    fun deleteTrack(trackItem: TrackItem) = viewModelScope.launch {
        dao.deleteTrack(trackItem)
    }

    class ViewModelFactory(private val db: MainDB) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(db) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}