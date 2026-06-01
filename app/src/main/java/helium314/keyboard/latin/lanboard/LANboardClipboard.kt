package helium314.keyboard.latin.lanboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Shared helper for the v2 clipboard fallback (spec §5.5).
 *
 * Live transcriptions are copied to the system clipboard in parallel with commitText() as a
 * recoverability safety net for fields that drop or truncate committed text. Deliberately does
 * NOT set EXTRA_IS_SENSITIVE — that would dot-mask the paste preview and defeat the purpose
 * (§5.5). Sensitive-field gating is the caller's responsibility: §5.3 keeps that content off
 * every persistence surface, so callers skip the copy for password/no-suggestion fields.
 */
object LANboardClipboard {
    private const val LABEL = "LANboard transcription"

    fun copy(context: Context, text: String) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(LABEL, text))
    }
}
