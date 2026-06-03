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

        // §7.1 dual-path: recording is ALWAYS allowed regardless of server reachability. On commit the
        // local WAV is written unconditionally and queued if the send fails (§7.2 network-down) — that
        // is the entire point of the pending queue + §7.4 delayed banner ("audio is never lost to a
        // transient network failure"). Server health gates only the ring color and the retry, NEVER
        // capture. (Blocking capture here was a latent bug, previously masked by the stale-green
        // health flag.) If health is stale, kick a background re-check so the ring is current — it
        // updates via onHealthChanged — but don't block recording on its result.
        if (!serverHealthy && System.currentTimeMillis() - lastHealthCheck > 30_000) {
            checkHealth()
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
                        // stale green). The session health monitor (started on input-view open) then
                        // catches the server's return and drains the queued recording — no keyboard
                        // reopen needed (§7.3).
                        setServerHealthy(false)
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
        // Run a session-long health monitor (immediate first check + periodic re-checks). It keeps the
        // ring honest in BOTH directions without a keyboard reopen — server going down OR coming back —
        // and drains the pending queue when reachability returns (§7.3 auto-retry; §line-265 background
        // re-check). The old approach only re-checked on open/mic-tap/failed-send, so a disconnect while
        // idle went unnoticed until reopen.
        startHealthMonitor()
    }

    fun onInputViewFinished() {
        stopHealthMonitor()
        if (state == State.LISTENING) discardRecording()
        transcriptionJob?.cancel()
        audioFocusManager.release() // defensive: don't leak focus if torn down mid-transcription
    }

    fun release() {
        stopHealthMonitor()
        audioCaptureManager.release()
        transcriptionJob?.cancel()
        audioFocusManager.release()
    }

    /** Single write point for server reachability: updates the flag and notifies the listener (the mic
     *  ring) so its server-state stroke re-renders on async changes. */
    private fun setServerHealthy(value: Boolean) {
        val changed = value != serverHealthy
        serverHealthy = value
        lastHealthCheck = System.currentTimeMillis()
        if (changed) listener?.onHealthChanged(value) // on the Main scope; no cross-thread post needed
    }

    /** Session-long bidirectional health monitor: an immediate check on start, then periodic re-checks
     *  while the keyboard is up. Keeps the ring honest whether the server goes DOWN or comes back
     *  (§line-265 background re-check), and on a return to health drains the pending queue so the §7.4
     *  delayed-transcription dot appears without a keyboard reopen (§7.3). Skips ticks during a
     *  recording (LISTENING/TRANSCRIBING own the ring then). Stops on input-view finish / release. */
    private fun startHealthMonitor() {
        if (healthPollJob?.isActive == true) return
        healthPollJob = scope.launch {
            var firstTick = true
            while (isActive) {
                if (state == State.IDLE) {
                    val wasHealthy = serverHealthy
                    val healthy = whisperClient.checkHealth(config)
                    setServerHealthy(healthy) // re-renders the ring on any change (down OR up)
                    // Drain the queue when reachability returns, and once on open (firstTick) so a
                    // recording made while the server was down surfaces as soon as the keyboard shows.
                    if (healthy && (!wasHealthy || firstTick)) retryPending()
                }
                firstTick = false
                delay(HEALTH_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopHealthMonitor() {
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
