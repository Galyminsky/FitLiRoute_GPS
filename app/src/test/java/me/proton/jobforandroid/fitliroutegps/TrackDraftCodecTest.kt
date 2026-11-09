package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.location.DraftPoint
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftCodec
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftRecovery
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftRepository
import me.proton.jobforandroid.fitliroutegps.location.DraftBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class TrackDraftCodecTest {
    private val recording = TrackDraft(
        42, true, 1_000L, 125.5f, 3.2f,
        DraftPoint(52.5, 62.5),
        listOf(DraftPoint(52.4, 62.4), DraftPoint(52.5, 62.5)),
        2L, 8_000L
    )

    @Test fun restoresRecordingStateAndLastSourceFix() {
        assertEquals(recording, TrackDraftCodec.decode(TrackDraftCodec.encode(recording)))
    }

    @Test fun restoresStoppedDraftWithoutPoints() {
        val stopped = recording.copy(recording = false, points = emptyList(), sequence = 0)
        assertEquals(stopped, TrackDraftCodec.decode(TrackDraftCodec.encode(stopped)))
    }

    @Test fun rejectsTruncatedDraftInsteadOfTreatingItAsEmpty() {
        val encoded = TrackDraftCodec.encode(recording)
        assertThrows(IOException::class.java) {
            TrackDraftCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test fun rejectsCorruptedCoordinateInsteadOfRestoringWrongRoute() {
        val encoded = TrackDraftCodec.encode(recording)
        encoded[encoded.size - 12] = (encoded[encoded.size - 12].toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { TrackDraftCodec.decode(encoded) }
    }

    @Test fun resumeExcludesTimeSpentWithProcessStopped() {
        val restoredStart = TrackDraftRecovery.resumedStartTime(recording, 100_000L)
        assertEquals(7_000L, 100_000L - restoredStart)
    }

    @Test fun repositoryReplacesCheckpointAndDeletesOnlyWhenRequested() {
        var stored: ByteArray? = null
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read() = stored
            override fun write(bytes: ByteArray) { stored = bytes.copyOf() }
            override fun delete() { stored = null }
        })
        assertEquals(null, repository.read())
        repository.write(recording)
        val stopped = recording.copy(recording = false, sequence = 3L, updatedAt = 9_000L)
        repository.write(stopped)
        assertEquals(stopped, repository.read())
        repository.delete()
        assertEquals(null, repository.read())
    }
}
