package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.location.GpsRecordingState
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecordingStateTest {
    @Test fun startIsNotActiveUntilRegistrationSucceeds() {
        val state = GpsRecordingState()
        assertEquals(GpsRecordingState.Value.STARTING, state.value)
        state.registrationSucceeded()
        assertEquals(GpsRecordingState.Value.ACTIVE, state.value)
    }

    @Test fun unavailableAndRestoredRemainOneRecording() {
        val state = GpsRecordingState()
        state.registrationSucceeded()
        state.unavailable()
        assertEquals(GpsRecordingState.Value.UNAVAILABLE, state.value)
        state.registrationSucceeded()
        assertEquals(GpsRecordingState.Value.ACTIVE, state.value)
    }

    @Test fun failedStartCanRecoverWithoutCreatingAnotherRecording() {
        val state = GpsRecordingState()
        state.unavailable()
        assertEquals(GpsRecordingState.Value.UNAVAILABLE, state.value)
        state.registrationSucceeded()
        assertEquals(GpsRecordingState.Value.ACTIVE, state.value)
    }

    @Test fun stopCannotBeReactivatedByLateRegistration() {
        val state = GpsRecordingState()
        state.registrationSucceeded()
        state.unavailable()
        state.stopped()
        state.registrationSucceeded()
        assertEquals(GpsRecordingState.Value.STOPPED, state.value)
    }
}
