package me.proton.jobforandroid.fitliroutegps.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One disk writer. Closing drains its latest queued snapshot without blocking the caller. */
class ConflatedDraftWriter(private val write: suspend (TrackDraft) -> Unit) {
    private data class Request(val draft: TrackDraft, val completion: CompletableDeferred<Boolean>?)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Request>(Channel.CONFLATED) { it.completion?.complete(false) }
    private val worker: Job = scope.launch {
        for (request in requests) {
            val saved = try { write(request.draft); true } catch (_: Exception) { false }
            request.completion?.complete(saved)
        }
    }.also { job -> job.invokeOnCompletion { scope.cancel() } }

    fun offer(draft: TrackDraft): Boolean = requests.trySend(Request(draft, null)).isSuccess

    suspend fun writeAndAwait(draft: TrackDraft): Boolean {
        val completion = CompletableDeferred<Boolean>()
        if (requests.trySend(Request(draft, completion)).isFailure) return false
        return completion.await()
    }

    /** Must be called after producers have stopped; the worker finishes queued work before exit. */
    fun finish(lastSnapshot: TrackDraft? = null): Job {
        if (lastSnapshot != null) offer(lastSnapshot)
        requests.close()
        return worker
    }
}
