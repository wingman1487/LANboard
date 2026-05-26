package helium314.keyboard.latin.lanboard

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

class PendingRecordingsManager(context: Context) {

    companion object {
        private const val TAG = "PendingRecordings"
        private const val PENDING_DIR = "pending_recordings"
        private const val META_SUFFIX = ".meta"
        private const val WAV_SUFFIX = ".wav"
        private val EXPIRY_MS = TimeUnit.DAYS.toMillis(7)
        private val GRACE_PERIOD_MS = TimeUnit.MINUTES.toMillis(30)
    }

    data class PendingRecording(
        val id: String,
        val wavFile: File,
        val createdAt: Long,
        val transcription: String? = null,
        val transcribedAt: Long = 0
    )

    private val pendingDir: File = File(context.filesDir, PENDING_DIR).apply { mkdirs() }

    fun savePending(id: String, wavData: ByteArray): File {
        val wavFile = File(pendingDir, "$id$WAV_SUFFIX")
        wavFile.writeBytes(wavData)

        val metaFile = File(pendingDir, "$id$META_SUFFIX")
        metaFile.writeText("created=${System.currentTimeMillis()}\nstatus=pending\n")

        Log.d(TAG, "Saved pending recording: $id (${wavData.size} bytes)")
        return wavFile
    }

    fun markTranscribed(id: String, text: String) {
        val metaFile = File(pendingDir, "$id$META_SUFFIX")
        if (metaFile.exists()) {
            val existing = metaFile.readText()
            metaFile.writeText(
                existing.replace("status=pending", "status=transcribed") +
                    "transcribed_at=${System.currentTimeMillis()}\ntext=$text\n"
            )
        }
    }

    fun markForDeletion(id: String) {
        val metaFile = File(pendingDir, "$id$META_SUFFIX")
        if (metaFile.exists()) {
            val existing = metaFile.readText()
            metaFile.writeText(
                existing.replace(Regex("status=\\w+"), "status=delete_after_grace") +
                    "delete_at=${System.currentTimeMillis() + GRACE_PERIOD_MS}\n"
            )
        }
    }

    fun deletePending(id: String) {
        File(pendingDir, "$id$WAV_SUFFIX").delete()
        File(pendingDir, "$id$META_SUFFIX").delete()
        Log.d(TAG, "Deleted recording: $id")
    }

    fun getPendingRecordings(): List<PendingRecording> {
        val recordings = mutableListOf<PendingRecording>()
        val metaFiles = pendingDir.listFiles { _, name -> name.endsWith(META_SUFFIX) } ?: return recordings

        for (metaFile in metaFiles) {
            val id = metaFile.nameWithoutExtension
            val wavFile = File(pendingDir, "$id$WAV_SUFFIX")
            if (!wavFile.exists()) {
                metaFile.delete()
                continue
            }

            val meta = parseMeta(metaFile)
            val status = meta["status"] ?: "pending"
            val createdAt = meta["created"]?.toLongOrNull() ?: 0L

            if (status == "pending") {
                recordings.add(PendingRecording(id, wavFile, createdAt))
            } else if (status == "transcribed") {
                recordings.add(
                    PendingRecording(
                        id, wavFile, createdAt,
                        transcription = meta["text"],
                        transcribedAt = meta["transcribed_at"]?.toLongOrNull() ?: 0L
                    )
                )
            }
        }

        return recordings.sortedBy { it.createdAt }
    }

    fun getPendingCount(): Int {
        val metaFiles = pendingDir.listFiles { _, name -> name.endsWith(META_SUFFIX) } ?: return 0
        return metaFiles.count { file ->
            val meta = parseMeta(file)
            meta["status"] == "pending"
        }
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val metaFiles = pendingDir.listFiles { _, name -> name.endsWith(META_SUFFIX) } ?: return

        for (metaFile in metaFiles) {
            val id = metaFile.nameWithoutExtension
            val meta = parseMeta(metaFile)
            val createdAt = meta["created"]?.toLongOrNull() ?: 0L
            val status = meta["status"] ?: "pending"

            val shouldDelete = when {
                // Expired: older than 7 days
                status == "pending" && now - createdAt > EXPIRY_MS -> true
                // Grace period passed for successful transcriptions
                status == "delete_after_grace" -> {
                    val deleteAt = meta["delete_at"]?.toLongOrNull() ?: 0L
                    now > deleteAt
                }
                else -> false
            }

            if (shouldDelete) {
                deletePending(id)
            }
        }
    }

    private fun parseMeta(file: File): Map<String, String> {
        return try {
            file.readLines()
                .filter { it.contains('=') }
                .associate { line ->
                    val (key, value) = line.split('=', limit = 2)
                    key.trim() to value.trim()
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
