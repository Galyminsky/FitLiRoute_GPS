package me.proton.jobforandroid.fitliroutegps.location

/** State of location delivery for one recording; unavailable never ends the route. */
class GpsRecordingState {
    enum class Value { STARTING, ACTIVE, UNAVAILABLE, STOPPED }

    var value = Value.STARTING
        private set

    fun registrationSucceeded() {
        if (value == Value.STARTING || value == Value.UNAVAILABLE) value = Value.ACTIVE
    }

    fun unavailable() {
        if (value != Value.STOPPED) value = Value.UNAVAILABLE
    }

    fun stopped() { value = Value.STOPPED }
}
