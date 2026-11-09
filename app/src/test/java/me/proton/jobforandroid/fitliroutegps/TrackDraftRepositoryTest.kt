package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.location.DraftBytes
import me.proton.jobforandroid.fitliroutegps.location.DraftPoint
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftCodec
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class TrackDraftRepositoryTest {
    private val draft = TrackDraft(17, false, 1_000L, 0f, 0f, null, emptyList(), 0L, 2_000L)

    @Test fun damagedBytesAreReportedAndRetainedForRecovery() {
        val stored = TrackDraftCodec.encode(draft).copyOf(20)
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read() = stored
            override fun write(bytes: ByteArray) = error("Unexpected write")
            override fun delete() = error("Unexpected delete")
        })
        assertThrows(IOException::class.java) { repository.read() }
        assertEquals(20, stored.size)
    }

    @Test fun failedWriteDoesNotDeletePreviousCheckpoint() {
        val previous = TrackDraftCodec.encode(draft)
        var stored = previous.copyOf()
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read() = stored.copyOf()
            override fun write(bytes: ByteArray) { throw IOException("Storage full") }
            override fun delete() = error("Unexpected delete")
        })
        assertThrows(IOException::class.java) {
            repository.write(draft.copy(recording = true, sequence = 1L))
        }
        assertArrayEquals(previous, stored)
        assertEquals(draft, repository.read())
    }

    @Test fun failedDeleteLeavesStoredCheckpointVisible() {
        val stored = TrackDraftCodec.encode(draft)
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read() = stored
            override fun write(bytes: ByteArray) = error("Unexpected write")
            override fun delete() { throw IOException("Cannot delete") }
        })
        assertThrows(IOException::class.java) { repository.delete() }
        assertEquals(draft, repository.read())
    }

    @Test fun persistedSnapshotDoesNotFollowLaterPointListMutations() {
        var stored: ByteArray? = null
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read() = stored
            override fun write(bytes: ByteArray) { stored = bytes.copyOf() }
            override fun delete() { stored = null }
        })
        val points = mutableListOf(DraftPoint(52.0, 62.0))
        repository.write(draft.copy(points = points))
        points.add(DraftPoint(53.0, 63.0))
        assertEquals(listOf(DraftPoint(52.0, 62.0)), repository.read()?.points)
    }
}
