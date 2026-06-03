package helium314.keyboard.latin.lanboard

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.suggestions.SuggestionStripView
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
    private var quickPicker: QuickPickerController? = null

    // §5.6 quick-picker gesture tracker state (one continuous DOWN→MOVE→UP on the mic container).
    private var gestureDownState: VoiceInputController.State? = null
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureLongPressFired = false
    private var gestureDragged = false
    private var gesturePickerOpen = false
    private val touchSlopPx by lazy { ViewConfiguration.get(ime).scaledTouchSlop }
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val pickerLongPress = Runnable {
        if (gesturePickerOpen) return@Runnable
        gestureLongPressFired = true
        if (gestureDownState == VoiceInputController.State.IDLE) {
            // Idle long-press opens the §5.6 picker (no-op at ≤1 preset → open() returns false).
            gesturePickerOpen = quickPicker?.open() ?: false
        } else {
            // LISTENING/TRANSCRIBING long-press keeps the existing discard, with the framework haptic
            // the removed setOnLongClickListener used to provide for free.
            micContainer?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            voiceController.onMicLongPress()
        }
    }

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

            override fun onHealthChanged(healthy: Boolean) {
                // Re-render the ring's server-state stroke when reachability changes asynchronously
                // (health check, failed send, or in-session poll). Only while IDLE — LISTENING /
                // TRANSCRIBING own the ring during a recording and must not be overwritten.
                if (voiceController.getState() == VoiceInputController.State.IDLE) {
                    micRingView?.state = if (healthy)
                        MicRingView.State.IDLE_OK
                    else
                        MicRingView.State.IDLE_UNREACHABLE
                }
            }
        }
    }

    fun onInputViewCreated(view: View) {
        micRingView = view.findViewById(R.id.lb_mic_ring)
        micIcon = view.findViewById(R.id.lb_mic_icon)
        micContainer = view.findViewById(R.id.lb_mic_ring_container)
        val strip = view.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        suggestionStripView = strip

        // §5.6 quick-picker: a non-touchable overlay stacked above the strip row. Created here, shown
        // only on an idle-ring long-press; never starts recording or touches the §7.3 health monitor.
        val overlayHost = view.findViewById<FrameLayout>(R.id.lb_strip_overlay_host)
        val mc = micContainer
        quickPicker = if (overlayHost != null && strip != null && mc != null)
            QuickPickerController(overlayHost, strip, mc, presetManager) { name -> onPickerResolve(name) }
        else null

        // One touch tracker replaces the click/long-click pair (§5.6). It re-dispatches tap (listen/commit)
        // and listening-long-press (discard) exactly as before, re-adding the framework haptic + click /
        // accessibility (performClick) those listeners gave for free, and owns the idle-long-press → picker
        // branch. Returning true claims the whole DOWN→MOVE→UP stream so the finger can slide onto a pill.
        mc?.setOnClickListener { voiceController.onMicTap() } // body fires only via performClick()
        mc?.setOnTouchListener { _, ev -> onMicTouch(ev) }

        // §7.4 delayed-transcription banner (expanded surface). Insert/Copy apply the §8.2
        // substitutions just like the live-commit path before the text leaves the banner.
        transcriptionBanner = DelayedTranscriptionBanner(
            view,
            // §7.4 Phase 2 dot host: the strip owns the ▸ key the collapsed badge sits on (§9.2 tap-priority).
            dotHost = strip,
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

    /**
     * §5.6 single-gesture tracker on the mic container. Re-dispatches the existing tap (listen/commit)
     * and listening-long-press (discard) and owns the idle-long-press → picker branch. The picker path
     * never calls onMicTap()/setState, so recording, audio focus and the §7.3 health monitor are untouched.
     */
    private fun onMicTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureDownState = voiceController.getState()
                gestureDownX = ev.rawX
                gestureDownY = ev.rawY
                gestureLongPressFired = false
                gestureDragged = false
                gesturePickerOpen = false
                mainHandler.postDelayed(pickerLongPress, longPressTimeoutMs)
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesturePickerOpen) {
                    quickPicker?.onHover(ev.rawX, ev.rawY)
                } else if (!gestureLongPressFired) {
                    val dx = ev.rawX - gestureDownX
                    val dy = ev.rawY - gestureDownY
                    if (dx * dx + dy * dy > (touchSlopPx * touchSlopPx).toFloat()) {
                        // A drag before the long-press timer fired is a scroll/slip, not a picker open.
                        gestureDragged = true
                        mainHandler.removeCallbacks(pickerLongPress)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(pickerLongPress)
                if (gesturePickerOpen) {
                    quickPicker?.resolveOnUp()?.let { onPickerResolve(it) }
                } else if (!gestureLongPressFired && !gestureDragged) {
                    // Clean tap → route through performClick() so click/accessibility (TalkBack) fire too.
                    micContainer?.performClick()
                }
                gesturePickerOpen = false
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(pickerLongPress)
                if (gesturePickerOpen) quickPicker?.abort()
                gesturePickerOpen = false
            }
        }
        return true
    }

    /** Resolve the §5.6 picker onto [name]: switch the active preset and re-tint the mic (§6.2). */
    private fun onPickerResolve(name: String) {
        presetManager.setActivePreset(name)
        voiceController.activePreset = presetManager.getActivePreset()?.promptText
        applyActivePresetColor() // Step 5 makes this a one-shot mic-tint crossfade
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
        // §5.6: retract any open quick-picker when the session ends.
        quickPicker?.clearSession()
        mainHandler.removeCallbacks(pickerLongPress)
        gesturePickerOpen = false
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
        // Initial best-guess on keyboard open from the last-known flag; the async health check then
        // corrects it via onHealthChanged. Use UNREACHABLE (not the amber "checking") for the
        // not-healthy case — spec §line-265: don't flash "checking" on routine re-checks.
        micRingView?.state = if (voiceController.isServerHealthy())
            MicRingView.State.IDLE_OK
        else
            MicRingView.State.IDLE_UNREACHABLE
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
