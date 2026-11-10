package me.proton.jobforandroid.fitliroutegps.location

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32

data class DraftPoint(val latitude: Double, val longitude: Double)

data class TrackDraft(
    val id: Int,
    val recording: Boolean,
    val startTime: Long,
    val distance: Float,
    val speed: Float,
    val lastPoint: DraftPoint?,
    val points: List<DraftPoint>,
    val sequence: Long,
    val updatedAt: Long
)

object TrackDraftRecovery {
    fun resumedStartTime(draft: TrackDraft, now: Long): Long =
        now - (draft.updatedAt - draft.startTime)
}

/** Strict format so a torn or invalid draft is never silently treated as an empty track. */
object TrackDraftCodec {
    private const val MAGIC = 0x46545231
    private const val MAX_POINTS = 1_000_000

    fun encode(draft: TrackDraft): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(draft.id)
            out.writeBoolean(draft.recording)
            out.writeLong(draft.startTime)
            out.writeFloat(draft.distance)
            out.writeFloat(draft.speed)
            out.writeBoolean(draft.lastPoint != null)
            draft.lastPoint?.let { out.writeDouble(it.latitude); out.writeDouble(it.longitude) }
            out.writeLong(draft.sequence)
            out.writeLong(draft.updatedAt)
            out.writeInt(draft.points.size)
            draft.points.forEach { out.writeDouble(it.latitude); out.writeDouble(it.longitude) }
        }
    }.toByteArray().let { payload ->
        ByteArrayOutputStream().also { bytes ->
            bytes.write(payload)
            DataOutputStream(bytes).writeLong(CRC32().apply { update(payload) }.value)
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): TrackDraft {
        if (bytes.size < 54) throw IOException("Truncated draft")
        val payloadSize = bytes.size - 8
        val checksum = DataInputStream(ByteArrayInputStream(bytes, payloadSize, 8)).readLong()
        if (CRC32().apply { update(bytes, 0, payloadSize) }.value != checksum) {
            throw IOException("Corrupt draft")
        }
        return DataInputStream(ByteArrayInputStream(bytes, 0, payloadSize)).use { input ->
        if (input.readInt() != MAGIC) throw IOException("Unknown draft format")
        val id = input.readInt()
        val recording = input.readBoolean()
        val startTime = input.readLong()
        val distance = input.readFloat()
        val speed = input.readFloat()
        val lastPoint = if (input.readBoolean()) DraftPoint(input.readDouble(), input.readDouble()) else null
        val sequence = input.readLong()
        val updatedAt = input.readLong()
        val count = input.readInt()
        if (id <= 0 || startTime <= 0 || updatedAt < startTime || sequence < 0 || count < 0 || count > MAX_POINTS ||
            !distance.isFinite() || distance < 0 || !speed.isFinite() ||
            payloadSize != 46 + (if (lastPoint == null) 0 else 16) + count * 16
        ) throw IOException("Invalid draft")
        val points = List(count) { DraftPoint(input.readDouble(), input.readDouble()) }
        if ((lastPoint != null && !validPoint(lastPoint)) || points.any { !validPoint(it) }) {
            throw IOException("Invalid coordinates")
        }
        TrackDraft(id, recording, startTime, distance, speed, lastPoint, points, sequence, updatedAt)
        }
    }

    private fun validPoint(point: DraftPoint): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
}

interface DraftBytes {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun delete()
}

class TrackDraftRepository(private val bytes: DraftBytes) {
    @Synchronized fun read(): TrackDraft? = bytes.read()?.let(TrackDraftCodec::decode)
    @Synchronized fun write(draft: TrackDraft) = bytes.write(TrackDraftCodec.encode(draft))
    @Synchronized fun delete() = bytes.delete()
}

class TrackDraftStore(context: Context) {
    private val repository = TrackDraftRepository(object : DraftBytes {
        private val file = AtomicFile(File(context.noBackupFilesDir, "active_track.draft"))

        override fun read(): ByteArray? = if (file.baseFile.exists() ||
            File(file.baseFile.path + ".bak").exists()) {
            file.openRead().use { it.readBytes() }
        } else null

        override fun write(bytes: ByteArray) {
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }

        override fun delete() = file.delete()
    })

    fun read(): TrackDraft? = repository.read()
    fun write(draft: TrackDraft) = repository.write(draft)
    fun delete() = repository.delete()
}
