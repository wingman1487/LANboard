package helium314.keyboard.latin.lanboard

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LANboardBridge(private val ime: LatinIME) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val config = LANboardConfig(ime)
    private val presetManager = VoicePresetManager(ime)
    private val substitutionManager = SubstitutionManager(ime)
    private val voiceController = VoiceInputController(ime, scope)

    private var micRingView: MicRingView? = null
    private var audioMeterView: AudioMeterView? = null
    private var micIcon: ImageView? = null
    private var micContainer: FrameLayout? = null
    private var terminalRowManager: TerminalRowManager? = null
    private var suggestionStripView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameRunner = object : Runnable {
        override fun run() {
            micRingView?.renderFrame()
            mainHandler.postDelayed(this, 33) // ~30fps
        }
    }
    private var frameRunnerActive = false

    init {
        voiceController.config = config.toWhisperConfig()
        voiceController.activePreset = presetManager.getActivePreset()?.promptText

        voiceController.listener = object : VoiceInputController.Listener {
            override fun onStateChanged(state: VoiceInputController.State) {
                updateVisualState(state)
            }

            override fun onAudioFrame(rms: Float) {
                micRingView?.updateAudio(rms)
                audioMeterView?.updateAudio(rms)
            }

            override fun onTranscriptionResult(text: String) {
                val processed = substitutionManager.applySubstitutions(text)
                ime.currentInputConnection?.commitText(processed, 1)
            }

            override fun onTranscriptionError(message: String) {
                // Error already shown via toast in VoiceInputController
            }

            override fun onPendingTranscription(id: String, text: String) {
                // TODO: show pending transcription banner
            }
        }
    }

    fun onInputViewCreated(view: View) {
        micRingView = view.findViewById(R.id.lb_mic_ring)
        audioMeterView = view.findViewById(R.id.lb_audio_meter)
        micIcon = view.findViewById(R.id.lb_mic_icon)
        micContainer = view.findViewById(R.id.lb_mic_ring_container)
        suggestionStripView = view.findViewById(R.id.suggestion_strip_view)

        micContainer?.setOnClickListener { voiceController.onMicTap() }
        micContainer?.setOnLongClickListener {
            voiceController.onMicLongPress()
            true
        }

        // Terminal row
        terminalRowManager = TerminalRowManager(view).apply {
            init()
            isVisible = config.terminalRowDefault
            setKeyEventSender { keyCode, metaState ->
                val ic = ime.currentInputConnection ?: return@setKeyEventSender
                val now = System.currentTimeMillis()
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
            }
        }

        startFrameRunner()
    }

    fun onInputViewStarted(editorInfo: EditorInfo?) {
        voiceController.config = config.toWhisperConfig()
        voiceController.activePreset = presetManager.getActivePreset()?.promptText

        // Detect sensitive input fields
        if (editorInfo != null) {
            val inputType = editorInfo.inputType
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            voiceController.isSensitiveField = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        }

        voiceController.onInputViewStarted()
        updateRingForServerState()
    }

    fun onInputViewFinished() {
        voiceController.onInputViewFinished()
        stopFrameRunner()
    }

    fun toggleTerminalRow() {
        terminalRowManager?.let { it.isVisible = !it.isVisible }
    }

    private fun updateVisualState(state: VoiceInputController.State) {
        when (state) {
            VoiceInputController.State.IDLE -> {
                micRingView?.state = if (voiceController.isServerHealthy())
                    MicRingView.State.IDLE_OK
                else
                    MicRingView.State.IDLE_UNREACHABLE
                audioMeterView?.visibility = View.GONE
                suggestionStripView?.visibility = View.VISIBLE
                micIcon?.setColorFilter(
                    ime.getColor(R.color.lb_text_secondary)
                )
            }
            VoiceInputController.State.LISTENING -> {
                micRingView?.state = MicRingView.State.LISTENING
                suggestionStripView?.visibility = View.GONE
                audioMeterView?.visibility = View.VISIBLE
                micIcon?.setColorFilter(
                    ime.getColor(R.color.lb_cyan)
                )
            }
            VoiceInputController.State.TRANSCRIBING -> {
                micRingView?.state = MicRingView.State.TRANSCRIBING
                audioMeterView?.visibility = View.GONE
                suggestionStripView?.visibility = View.VISIBLE
                micIcon?.setColorFilter(
                    ime.getColor(R.color.lb_cyan)
                )
            }
        }
    }

    private fun updateRingForServerState() {
        micRingView?.state = if (voiceController.isServerHealthy())
            MicRingView.State.IDLE_OK
        else
            MicRingView.State.IDLE_CHECKING
    }

    private fun startFrameRunner() {
        if (!frameRunnerActive) {
            frameRunnerActive = true
            mainHandler.post(frameRunner)
        }
    }

    private fun stopFrameRunner() {
        frameRunnerActive = false
        mainHandler.removeCallbacks(frameRunner)
    }
}
