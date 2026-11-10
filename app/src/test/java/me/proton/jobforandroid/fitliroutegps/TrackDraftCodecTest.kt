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
import java.nio.ByteBuffer
import java.util.zip.CRC32

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

    private fun withValidChecksum(bytes: ByteArray): ByteArray {
        val payloadSize = bytes.size - 8
        ByteBuffer.wrap(bytes).putLong(payloadSize, CRC32().apply {
            update(bytes, 0, payloadSize)
        }.value)
        return bytes
    }

    @Test fun restoresBoundaryCoordinatesAndMissingPreviousFix() {
        val draft = recording.copy(lastPoint = null,
            points = listOf(DraftPoint(-90.0, 180.0), DraftPoint(90.0, -180.0)))
        assertEquals(draft, TrackDraftCodec.decode(TrackDraftCodec.encode(draft)))
    }

    @Test fun rejectsInvalidMetadataEvenWhenChecksumIsValid() {
        val mutations: List<(ByteBuffer) -> Unit> = listOf(
            { it.putInt(4, 0) },
            { it.putLong(9, 0L) },
            { it.putFloat(17, -1f) },
            { it.putFloat(21, Float.NaN) },
            { it.putLong(42, -1L) },
            { it.putLong(50, 999L) }
        )
        mutations.forEach { mutate ->
            val bytes = TrackDraftCodec.encode(recording)
            mutate(ByteBuffer.wrap(bytes))
            assertThrows(IOException::class.java) {
                TrackDraftCodec.decode(withValidChecksum(bytes))
            }
        }
    }

    @Test fun rejectsInvalidCoordinatesEvenWhenChecksumIsValid() {
        listOf(Double.NaN, 91.0).forEach { latitude ->
            val bytes = TrackDraftCodec.encode(recording)
            ByteBuffer.wrap(bytes).putDouble(26, latitude)
            assertThrows(IOException::class.java) {
                TrackDraftCodec.decode(withValidChecksum(bytes))
            }
        }
    }

    @Test fun rejectsUnknownFormatAndOversizedPointCountBeforeAllocation() {
        val unknown = TrackDraftCodec.encode(recording)
        ByteBuffer.wrap(unknown).putInt(0, 0)
        assertThrows(IOException::class.java) {
            TrackDraftCodec.decode(withValidChecksum(unknown))
        }
        val oversized = TrackDraftCodec.encode(recording)
        ByteBuffer.wrap(oversized).putInt(58, 1_000_001)
        assertThrows(IOException::class.java) {
            TrackDraftCodec.decode(withValidChecksum(oversized))
        }
    }
}
