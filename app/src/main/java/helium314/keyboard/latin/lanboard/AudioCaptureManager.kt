package helium314.keyboard.latin.lanboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioCaptureManager(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RMS_THRESHOLD = 0.005f
        private const val FRAME_INTERVAL_MS = 33L // ~30fps
    }

    interface AudioFrameListener {
        fun onAudioFrame(rms: Float)
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val audioBuffer = ByteArrayOutputStream()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameListener: AudioFrameListener? = null
    private var currentRms = 0f
    private var peakRms = 0f

    fun setFrameListener(listener: AudioFrameListener) {
        frameListener = listener
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun startRecording(): Boolean {
        if (isRecording) return true
        if (!hasPermission()) return false

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return false

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return false
        }

        audioBuffer.reset()
        isRecording = true
        peakRms = 0f
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            var lastFrameTime = System.currentTimeMillis()

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    // Write raw PCM to buffer
                    val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        byteBuffer.putShort(buffer[i])
                    }
                    synchronized(audioBuffer) {
                        audioBuffer.write(byteBuffer.array())
                    }

                    // Compute RMS (normalized to 0.0-1.0 range)
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTime >= FRAME_INTERVAL_MS) {
                        lastFrameTime = now
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toFloat() / Short.MAX_VALUE
                            sumSquares += sample * sample
                        }
                        currentRms = sqrt(sumSquares / read).toFloat()
                        if (currentRms > peakRms) peakRms = currentRms
                        mainHandler.post {
                            frameListener?.onAudioFrame(currentRms)
                        }
                    }
                }
            }
        }.apply {
            name = "LANboard-AudioCapture"
            start()
        }

        return true
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        recordingThread?.join(500)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmData: ByteArray
        synchronized(audioBuffer) {
            pcmData = audioBuffer.toByteArray()
            audioBuffer.reset()
        }

        currentRms = 0f
        peakRms = 0f
        return pcmData
    }

    fun discardRecording() {
        isRecording = false
        recordingThread?.join(500)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        synchronized(audioBuffer) {
            audioBuffer.reset()
        }
        currentRms = 0f
        peakRms = 0f
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    fun hasAudioAboveThreshold(): Boolean = peakRms >= RMS_THRESHOLD

    fun release() {
        discardRecording()
    }
}
