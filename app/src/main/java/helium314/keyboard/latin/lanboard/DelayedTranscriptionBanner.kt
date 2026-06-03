// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lanboard

import android.view.View
import android.widget.TextView
import helium314.keyboard.latin.R

/**
 * §7.4 delayed-transcription banner — controller for the expanded surface (Phase 1).
 *
 * When a queued recording transcribes after the network returns (VoiceInputController.retryPending →
 * LANboardBridge's onPendingTranscription), focus may be on a different field than when the audio was
 * captured, so the text must never auto-insert. This replaces the v1 non-actionable Toast with the
 * floating glass banner (visual contract delayed-transcription-banner.html): an amber dot + "Transcription
 * ready" label + ~60-char ellipsized preview + Insert / Copy / × controls, docked above the top strip.
 *
 * Session-scoped (§7.4): the banner persists for the current keyboard session until the user resolves it
 * (Insert / Copy / ×); [clearSession] drops it when the input view finishes. It does NOT auto-insert and
 * does NOT touch the Pending Queue — the transcription stays there regardless (§7.5), so nothing is lost
 * if the banner is dismissed. At most one expanded banner shows at a time; additional arrivals add a
 * "+N more" count and resolving the current one advances to the next (§7.4 multiple arrivals).
 *
 * Phase 1 builds the expanded surface only; the collapsed amber-dot-on-▸ state and its tap-priority
 * (§9.2) are Phase 2.
 */
class DelayedTranscriptionBanner(
    rootView: View,
    private val onInsert: (id: String, text: String) -> Unit,
    private val onCopy: (id: String, text: String) -> Unit,
) {
    private data class Item(val id: String, val text: String)

    private val banner: View? = rootView.findViewById(R.id.lb_transcription_banner)
    private val previewView: TextView? = rootView.findViewById(R.id.lb_banner_preview)
    private val countView: TextView? = rootView.findViewById(R.id.lb_banner_count)

    /** FIFO of transcriptions awaiting the user's decision; the head is the one currently shown. */
    private val queue = ArrayDeque<Item>()

    init {
        // Clip the amber left edge + glass body to the bg's rounded outline (layer-list item
        // gravity/width needs API 23; minSdk is 21, so we clip in code instead).
        banner?.clipToOutline = true

        banner?.findViewById<View>(R.id.lb_banner_insert)?.setOnClickListener {
            val current = queue.firstOrNull() ?: return@setOnClickListener
            onInsert(current.id, current.text)
            advance()
        }
        banner?.findViewById<View>(R.id.lb_banner_copy)?.setOnClickListener {
            val current = queue.firstOrNull() ?: return@setOnClickListener
            onCopy(current.id, current.text)
            advance()
        }
        banner?.findViewById<View>(R.id.lb_banner_discard)?.setOnClickListener {
            // × discards from the banner only; the recording stays in the Pending Queue (§7.4).
            advance()
        }
    }

    /** A queued recording just transcribed. Show it now, or queue it behind the current one. */
    fun enqueue(id: String, text: String) {
        if (text.isBlank()) return
        if (queue.any { it.id == id }) return // idempotent: retryPending re-fire shouldn't double-add
        queue.addLast(Item(id, text))
        render()
    }

    /** Session ended (input view finished) — the banner is session-scoped, so drop it. */
    fun clearSession() {
        queue.clear()
        hide()
    }

    private fun advance() {
        queue.removeFirstOrNull()
        render()
    }

    private fun render() {
        val current = queue.firstOrNull()
        if (current == null) {
            hide()
            return
        }
        previewView?.text = current.text

        val others = queue.size - 1
        countView?.let {
            if (others > 0) {
                it.text = it.context.getString(R.string.lb_transcription_banner_count, others)
                it.visibility = View.VISIBLE
            } else {
                it.visibility = View.GONE
            }
        }

        banner?.let {
            if (it.visibility != View.VISIBLE) {
                it.visibility = View.VISIBLE
                // One-shot fade-in on arrival — never looping, to honor §13.2 (no ambient animation).
                it.alpha = 0f
                it.animate().alpha(1f).setDuration(140L).start()
            }
        }
    }

    private fun hide() {
        banner?.animate()?.cancel()
        banner?.alpha = 1f
        banner?.visibility = View.GONE
    }
}
