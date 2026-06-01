package helium314.keyboard.latin.lanboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceInputController(
    private val context: Context,
    private val scope: CoroutineScope
) {

    enum class State { IDLE, LISTENING, TRANSCRIBING }

    interface Listener {
        fun onStateChanged(state: State)
        fun onAudioFrame(rms: Float)
        fun onTranscriptionResult(text: String)
        fun onTranscriptionError(message: String)
        fun onPendingTranscription(id: String, text: String)
    }

    private val audioCaptureManager = AudioCaptureManager(context)
    private val whisperClient = WhisperClient()
    private val pendingManager = PendingRecordingsManager(context)
    private val audioFocusManager = AudioFocusManager(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    var listener: Listener? = null
    var config = WhisperClient.Config(serverUrl = "")
    var activePreset: String? = null
    var isSensitiveField = false
    var pauseMediaDuringRecording = true

    private var state = State.IDLE
    private var transcriptionJob: Job? = null
    private var serverHealthy = false
    private var lastHealthCheck = 0L

    init {
        audioCaptureManager.setFrameListener(object : AudioCaptureManager.AudioFrameListener {
            override fun onAudioFrame(rms: Float) {
                listener?.onAudioFrame(rms)
            }
        })
    }

    fun onMicTap() {
        when (state) {
            State.IDLE -> startListening()
            State.LISTENING -> commitRecording()
            State.TRANSCRIBING -> { /* locked during transcription */ }
        }
    }

    fun onMicLongPress() {
        if (state == State.LISTENING) {
            discardRecording()
        }
    }

    private fun startListening() {
        if (config.serverUrl.isBlank()) {
            toast("Configure server URL in LANboard settings")
            return
        }

        if (!serverHealthy && System.currentTimeMillis() - lastHealthCheck > 30_000) {
            scope.launch {
                serverHealthy = whisperClient.checkHealth(config)
                lastHealthCheck = System.currentTimeMillis()
                if (serverHealthy) {
                    mainHandler.post { beginCapture() }
                } else {
                    mainHandler.post { toast("Server unreachable") }
                }
            }
            return
        }

        if (!serverHealthy) {
            toast("Server unreachable")
            return
        }

        beginCapture()
    }

    private fun beginCapture() {
        if (!audioCaptureManager.hasPermission()) {
            toast("Microphone permission required")
            return
        }

        if (audioCaptureManager.startRecording()) {
            setState(State.LISTENING)
        } else {
            toast("Failed to start recording")
        }
    }

    private fun commitRecording() {
        if (!audioCaptureManager.isCurrentlyRecording()) return

        val hasAudio = audioCaptureManager.hasAudioAboveThreshold()
        val pcmData = audioCaptureManager.stopRecording()

        if (pcmData.isEmpty() || !hasAudio) {
            toast("No speech detected")
            setState(State.IDLE)
            return
        }

        val wavData = WavEncoder.encodePcmToWav(pcmData)
        val recordingId = UUID.randomUUID().toString()

        setState(State.TRANSCRIBING)

        // Dual-path: write to local storage AND send to server simultaneously
        if (!isSensitiveField) {
            pendingManager.savePending(recordingId, wavData)
        }

        transcriptionJob = scope.launch {
            val result = whisperClient.transcribe(config, wavData, activePreset)

            mainHandler.post {
                if (result.success && result.text.isNotEmpty()) {
                    listener?.onTranscriptionResult(result.text)
                    if (!isSensitiveField) {
                        pendingManager.markForDeletion(recordingId)
                    }
                } else if (result.success && result.text.isEmpty()) {
                    toast(result.error ?: "No speech detected")
                    if (!isSensitiveField) {
                        pendingManager.deletePending(recordingId)
                    }
                } else {
                    // Network/server failure — local file is saved for retry
                    val errorMsg = result.error ?: "Transcription failed"
                    if (result.isAuthError) {
                        listener?.onTranscriptionError(errorMsg)
                    } else {
                        toast(if (isSensitiveField) errorMsg else "Saved locally — will retry when server is available")
                    }
                }
                setState(State.IDLE)
            }
        }
    }

    private fun discardRecording() {
        audioCaptureManager.discardRecording()
        setState(State.IDLE)
    }

    fun checkHealth() {
        scope.launch {
            serverHealthy = whisperClient.checkHealth(config)
            lastHealthCheck = System.currentTimeMillis()
        }
    }

    fun retryPending() {
        // §5.4: queue drains capture NO new audio, so they must NOT request audio focus.
        // Do not add an audioFocusManager.request() here.
        scope.launch {
            if (!serverHealthy) return@launch
            val pending = pendingManager.getPendingRecordings()
                .filter { it.transcription == null }
                .take(1)

            for (recording in pending) {
                val wavData = recording.wavFile.readBytes()
                val result = whisperClient.transcribe(config, wavData, activePreset)
                if (result.success && result.text.isNotEmpty()) {
                    pendingManager.markTranscribed(recording.id, result.text)
                    mainHandler.post {
                        listener?.onPendingTranscription(recording.id, result.text)
                    }
                }
            }
        }
    }

    fun onInputViewStarted() {
        checkHealth()
        pendingManager.cleanupExpired()
        if (serverHealthy) retryPending()
    }

    fun onInputViewFinished() {
        if (state == State.LISTENING) discardRecording()
        transcriptionJob?.cancel()
        audioFocusManager.release() // defensive: don't leak focus if torn down mid-transcription
    }

    fun release() {
        audioCaptureManager.release()
        transcriptionJob?.cancel()
        audioFocusManager.release()
    }

    private fun setState(newState: State) {
        state = newState
        // §5.4 media-pause via audio focus, owned at the single state choke point so no path can
        // leak focus. Request when recording starts; hold through TRANSCRIBING (avoids stop-start
        // cycling between back-to-back dictations); release when the interaction ends at IDLE.
        // Requesting never gates recording — the request's result is ignored here.
        when (newState) {
            State.LISTENING -> if (pauseMediaDuringRecording) audioFocusManager.request()
            State.IDLE -> audioFocusManager.release()
            State.TRANSCRIBING -> { /* keep focus through transcription */ }
        }
        listener?.onStateChanged(newState)
    }

    fun getState(): State = state
    fun isServerHealthy(): Boolean = serverHealthy
    fun getPendingCount(): Int = pendingManager.getPendingCount()

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
