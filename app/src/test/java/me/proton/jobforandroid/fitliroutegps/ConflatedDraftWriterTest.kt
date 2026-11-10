package me.proton.jobforandroid.fitliroutegps

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.proton.jobforandroid.fitliroutegps.location.ConflatedDraftWriter
import me.proton.jobforandroid.fitliroutegps.location.DraftBytes
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftCodec
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ConflatedDraftWriterTest {
    private fun draft(sequence: Long) = TrackDraft(7, true, 1_000L, 0f, 0f,
        null, emptyList(), sequence, 2_000L + sequence)

    @Test fun severalSnapshotsRetainTheLatestSequenceOnClose() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<Long>()
        val writer = ConflatedDraftWriter { snapshot ->
            if (snapshot.sequence == 1L) { entered.complete(Unit); release.await() }
            saved.add(snapshot.sequence)
        }
        assertTrue(writer.offer(draft(1)))
        withTimeout(2_000) { entered.await() }
        assertTrue(writer.offer(draft(2)))
        assertTrue(writer.offer(draft(3)))
        val finished = writer.finish(draft(4))
        release.complete(Unit)
        withTimeout(2_000) { finished.join() }
        assertEquals(listOf(1L, 4L), saved)
    }

    @Test fun delayedCheckpointWaitsForConfirmedWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = ConflatedDraftWriter { entered.complete(Unit); release.await() }
        val result = async { writer.writeAndAwait(draft(5)) }
        withTimeout(2_000) { entered.await() }
        assertFalse(result.isCompleted)
        release.complete(Unit)
        assertTrue(withTimeout(2_000) { result.await() })
        withTimeout(2_000) { writer.finish().join() }
    }

    @Test fun destroyWithPendingSnapshotDrainsTheFinalOne() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var onDisk = 0L
        val writer = ConflatedDraftWriter { snapshot ->
            if (snapshot.sequence == 1L) { entered.complete(Unit); release.await() }
            onDisk = snapshot.sequence
        }
        writer.offer(draft(1))
        withTimeout(2_000) { entered.await() }
        writer.offer(draft(2))
        val finished = writer.finish(draft(3))
        release.complete(Unit)
        withTimeout(2_000) { finished.join() }
        assertEquals(3L, onDisk)
    }

    @Test fun failedWriteKeepsPreviousCheckpointAndReportsFailure() = runBlocking {
        var onDisk = TrackDraftCodec.encode(draft(4))
        val repository = TrackDraftRepository(object : DraftBytes {
            override fun read(): ByteArray = onDisk
            override fun write(bytes: ByteArray) { throw IOException("disk full") }
            override fun delete() = error("Writer must not delete the draft")
        })
        val writer = ConflatedDraftWriter { repository.write(it) }
        assertFalse(withTimeout(2_000) { writer.writeAndAwait(draft(5)) })
        withTimeout(2_000) { writer.finish().join() }
        assertEquals(4L, TrackDraftCodec.decode(onDisk).sequence)
    }

    @Test fun repeatedStopDoesNotHangOrAcceptNewWrites() = runBlocking {
        val writer = ConflatedDraftWriter { }
        val first = writer.finish(draft(1))
        val second = writer.finish()
        assertEquals(first, second)
        assertFalse(writer.offer(draft(2)))
        withTimeout(2_000) { second.join() }
    }

    @Test fun writesNeverRunConcurrently() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val writer = ConflatedDraftWriter {
            val count = active.incrementAndGet()
            maxActive.accumulateAndGet(count, ::maxOf)
            try { Thread.sleep(20) } finally { active.decrementAndGet() }
        }
        repeat(10) { writer.offer(draft(it.toLong())) }
        withTimeout(2_000) { writer.finish(draft(10)).join() }
        assertEquals(1, maxActive.get())
    }
}
