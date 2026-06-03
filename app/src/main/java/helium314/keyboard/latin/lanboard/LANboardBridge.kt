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
    private var micIcon: ImageView? = null
    private var micContainer: FrameLayout? = null
    private var terminalRowManager: TerminalRowManager? = null
    private var suggestionStripView: View? = null
    private var transcriptionBanner: DelayedTranscriptionBanner? = null

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
            }

            override fun onTranscriptionResult(text: String) {
                val processed = substitutionManager.applySubstitutions(text)
                ime.currentInputConnection?.commitText(processed, 1)
                // v2 §5.5 clipboard fallback: mirror the committed text to the clipboard so a
                // dropped/truncated commit stays recoverable. Silent (the user sees the inserted
                // text). Skipped for sensitive fields, which §5.3 keeps off every persistence surface.
                if (config.copyTranscriptionsToClipboard && !voiceController.isSensitiveField) {
                    LANboardClipboard.copy(ime, processed)
                }
            }

            override fun onTranscriptionError(message: String) {
                // Error already shown via toast in VoiceInputController
            }

            override fun onPendingTranscription(id: String, text: String) {
                // §7.4: surface the delayed transcription in the actionable banner (Insert / Copy / ×)
                // instead of the v1 non-actionable Toast — focus may be on a different field now.
                transcriptionBanner?.enqueue(id, text)
            }
        }
    }

    fun onInputViewCreated(view: View) {
        micRingView = view.findViewById(R.id.lb_mic_ring)
        micIcon = view.findViewById(R.id.lb_mic_icon)
        micContainer = view.findViewById(R.id.lb_mic_ring_container)
        suggestionStripView = view.findViewById(R.id.suggestion_strip_view)

        micContainer?.setOnClickListener { voiceController.onMicTap() }
        micContainer?.setOnLongClickListener {
            voiceController.onMicLongPress()
            true
        }

        // §7.4 delayed-transcription banner (expanded surface). Insert/Copy apply the §8.2
        // substitutions just like the live-commit path before the text leaves the banner.
        transcriptionBanner = DelayedTranscriptionBanner(
            view,
            onInsert = { id, text ->
                val processed = substitutionManager.applySubstitutions(text)
                ime.currentInputConnection?.commitText(processed, 1)
                voiceController.markInserted(id)
            },
            onCopy = { _, text ->
                val processed = substitutionManager.applySubstitutions(text)
                LANboardClipboard.copy(ime, processed)
                android.widget.Toast.makeText(
                    ime, R.string.lb_copied_to_clipboard, android.widget.Toast.LENGTH_SHORT
                ).show()
            },
        )

        // §6.2: the mic icon + listening spike tips carry the active preset's color (the per-app
        // auto-select feedback) from the moment the view exists — visible at idle, before recording.
        applyActivePresetColor()

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
        voiceController.pauseMediaDuringRecording = config.pauseMediaDuringRecording

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
        applyActivePresetColor()
        startFrameRunner()
        syncTerminalRow()
    }

    /** §6.2: push the active preset's color into the mic icon (all states) and the ring spike-tip
     *  color. The ring STROKE stays the server-state channel and is NOT tinted here. Falls back to
     *  General cyan via [VoicePresetManager.Preset.colorInt] / [LBPresetPalette] so a bad stored hex
     *  never crashes the IME. */
    private fun applyActivePresetColor() {
        val preset = presetManager.getActivePreset()
        val colorInt = preset?.colorInt() ?: LBPresetPalette.GENERAL_CYAN
        val isGeneral = preset == null || preset.name == "General"
        micRingView?.setActivePreset(colorInt, isGeneral)
        micIcon?.setColorFilter(colorInt)
    }

    private fun syncTerminalRow() {
        val shouldShow = config.terminalRowDefault
        terminalRowManager?.let {
            if (it.isVisible != shouldShow) {
                it.isVisible = shouldShow
            }
        }
    }

    fun onInputViewFinished() {
        voiceController.onInputViewFinished()
        // §7.4: the banner is session-scoped — drop any unresolved transcription when the session ends.
        transcriptionBanner?.clearSession()
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
                suggestionStripView?.visibility = View.VISIBLE
            }
            VoiceInputController.State.LISTENING -> {
                micRingView?.state = MicRingView.State.LISTENING
            }
            VoiceInputController.State.TRANSCRIBING -> {
                micRingView?.state = MicRingView.State.TRANSCRIBING
                suggestionStripView?.visibility = View.VISIBLE
            }
        }
        // §6.2: the mic icon shows the active preset color in ALL three states (the ring stroke is the
        // separate server-state channel). Re-applied here so a preset switch reflects immediately.
        applyActivePresetColor()
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
