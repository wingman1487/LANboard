package helium314.keyboard.latin.lanboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        /** Server reachability changed (from a health check, a failed send, or the in-session poll).
         *  Lets the mic ring re-render its server-state stroke without waiting for a record/transcribe
         *  state transition — fixes the "ring not re-rendering on async health-check return" bug. */
        fun onHealthChanged(healthy: Boolean)
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
    private var healthPollJob: Job? = null

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
                setServerHealthy(whisperClient.checkHealth(config))
                if (serverHealthy) {
                    beginCapture()
                } else {
                    toast("Server unreachable")
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
                    // Network/server failure — the local file is saved for retry.
                    val errorMsg = result.error ?: "Transcription failed"
                    if (result.isAuthError) {
                        // The server responded (auth rejected) → it's reachable; leave health alone.
                        listener?.onTranscriptionError(errorMsg)
                    } else {
                        // A failed send is direct evidence the server is unreachable: clear the health
                        // flag so the ring reads UNREACHABLE on the setState(IDLE) below (instead of a
                        // stale green), and start the in-session poll so the queued recording auto-
                        // retries the moment the server returns — no keyboard reopen needed (§7.3).
                        setServerHealthy(false)
                        toast(if (isSensitiveField) errorMsg else "Saved locally — will retry when server is available")
                        if (!isSensitiveField) startHealthPolling()
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
            setServerHealthy(whisperClient.checkHealth(config))
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
        pendingManager.cleanupExpired()
        // §7.3 auto-retry: gate the pending-queue drain on the RESULT of this session's health check,
        // not the stale serverHealthy flag. checkHealth() updates serverHealthy on a background
        // coroutine, so reading it synchronously right after missed the first keyboard-open after the
        // server reconnected (the recording only drained on a manual Settings retry). Mirror the
        // mic-tap path: run the health check and the retry in one coroutine so the retry sees the
        // fresh result.
        scope.launch {
            setServerHealthy(whisperClient.checkHealth(config))
            if (serverHealthy) {
                retryPending()
            } else if (pendingManager.getPendingCount() > 0) {
                // Server still down but recordings are waiting — poll so they auto-retry in-session.
                startHealthPolling()
            }
        }
    }

    fun onInputViewFinished() {
        stopHealthPolling()
        if (state == State.LISTENING) discardRecording()
        transcriptionJob?.cancel()
        audioFocusManager.release() // defensive: don't leak focus if torn down mid-transcription
    }

    fun release() {
        stopHealthPolling()
        audioCaptureManager.release()
        transcriptionJob?.cancel()
        audioFocusManager.release()
    }

    /** Single write point for server reachability: updates the flag, stops the poll once healthy, and
     *  notifies the listener (the mic ring) so its server-state stroke re-renders on async changes. */
    private fun setServerHealthy(value: Boolean) {
        val changed = value != serverHealthy
        serverHealthy = value
        lastHealthCheck = System.currentTimeMillis()
        if (value) stopHealthPolling()
        if (changed) listener?.onHealthChanged(value) // on the Main scope; no cross-thread post needed
    }

    /** §7.3 in-session auto-fire (owner-chosen): while the server is unreachable, re-check health on a
     *  light interval; the moment it returns, green the ring (via [setServerHealthy]) and drain the
     *  pending queue so the delayed-transcription dot appears without needing a keyboard reopen. */
    private fun startHealthPolling() {
        if (healthPollJob?.isActive == true) return
        healthPollJob = scope.launch {
            while (isActive && !serverHealthy) {
                delay(HEALTH_POLL_INTERVAL_MS)
                if (whisperClient.checkHealth(config)) {
                    setServerHealthy(true) // greens the ring + cancels this poll
                    retryPending()         // surfaces the queued transcription via the banner dot
                    return@launch
                }
            }
        }
    }

    private fun stopHealthPolling() {
        healthPollJob?.cancel()
        healthPollJob = null
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

    /** §7.4: inserting a delayed transcription from the banner is a successful insertion, so the
     *  recording enters the §7.5 30-minute post-insertion grace before its local file is removed —
     *  mirroring the live-commit path's markForDeletion call. */
    fun markInserted(id: String) = pendingManager.markForDeletion(id)

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** In-session health re-check cadence while the server is unreachable (§7.3 auto-fire). */
        const val HEALTH_POLL_INTERVAL_MS = 5_000L
    }
}
