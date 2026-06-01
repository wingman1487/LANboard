package helium314.keyboard.latin.lanboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Owns AUDIOFOCUS_GAIN_TRANSIENT for the recording lifecycle (spec §5.4): well-behaved media
 * apps pause on focus loss and auto-resume when focus is abandoned. We request a clean pause
 * (GAIN_TRANSIENT, not _MAY_DUCK) because we want silence during dictation.
 *
 * Idempotent: repeated request()/release() are safe. Focus is advisory only — a denied request
 * (e.g. an in-progress call) NEVER blocks recording (§5.4 "record anyway").
 */
class AudioFocusManager(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // With GAIN_TRANSIENT we are the focus holder. Per §5.4 we keep recording even if focus is
    // lost externally (e.g. an incoming call mid-dictation), so loss callbacks are ignored.
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* intentionally ignored */ }

    private var request: AudioFocusRequest? = null
    private var held = false

    /** Request transient focus to pause other apps' media. Returns granted-status (informational only). */
    fun request(): Boolean {
        val am = audioManager ?: return false
        if (held) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            request = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    /** Abandon focus so paused media resumes. Safe to call when nothing is held. */
    fun release() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { am.abandonAudioFocusRequest(it) }
            request = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
        held = false
    }
}
